# Confluent ReST Proxy Walkthrough

Showcasing Confluent ReST Proxy with an in-depth walkthrough

## Getting Started

Start the containers:

```bash
docker container prune -f && docker-compose up
```

### Confirm that ReST proxy is running

Get the Cluster ID information using ReST Proxy:

```bash
curl -sS http://localhost:8082/v3/clusters | jq
```

To get just the Cluster ID, we can run:

```bash
curl -sS http://localhost:8082/v3/clusters | jq -r ".data[0].cluster_id"
```

## Exploring the Configuration

From here we can get the details for our CP cluster by running:

```bash
curl -sS http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ | jq
```

Let's explore the broker with the id of `1` (or `KAFKA_BROKER_ID: 1` in the `docker-compose` file):

```bash
curl -sS http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/brokers/1 | jq
```

Note that the output gives you information about two more configuration segments: `configs` and `partition-replicas`.

Let's take a look at the `/configs` endpoint:

```bash
curl -sS http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/brokers/1/configs | jq
```

This returns a very large amount of JSON data!  Let's zero in on a specific configuration item:

```bash
curl -sS http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/brokers/1/configs/compression.type | jq
```

You'll see something like this:

```json
{
  "kind": "KafkaBrokerConfig",
  "metadata": {
    "self": "http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/brokers/1/configs/compression.type",
    "resource_name": "crn:///kafka=ZWe3nnZwTrKSM0aM2doAxQ/broker=1/config=compression.type"
  },
  "cluster_id": "ZWe3nnZwTrKSM0aM2doAxQ",
  "name": "compression.type",
  "value": "producer",
  "is_read_only": false,
  "is_sensitive": false,
  "source": "DEFAULT_CONFIG",
  "synonyms": [
    {
      "name": "compression.type",
      "value": "producer",
      "source": "DEFAULT_CONFIG"
    }
  ],
  "broker_id": 1,
  "is_default": true
}
```

Note that there is some genuinely useful information being returned here - note that the value here is `"producer"`; this is important because `compression.type` can be configured (or if you prefer, enforced) on the broker or within your Producer code.

So this is telling us two things:

1. By default, compression is not configured at the broker level
2. Whenever compression is not configured, you should configure your application code (your producers) to use compression

If you configure a `compression.type` at the broker level and at the Producer level and these types differ, the broker will decompress the payload and re-compress it; you don't want to do this!

Let's look at a few other confguration settings for example:

```bash
curl -sS http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/brokers/1/configs/auto.create.topics.enable | jq
```

```bash
curl -sS http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/brokers/1/configs/message.max.bytes | jq
```

```bash
curl -sS http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/brokers/1/configs/default.replication.factor | jq
```

Let's get a full list of all the available configuration `name` items:

```bash
curl -sS http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/brokers/1/configs | jq -r ".data[].name"
```

As this example is using `jq`, we could get a subset by providing start and end items to return a subset of the data in a range.

Let's get the first 10 items (note that it's zero-indexed):

```bash
curl -sS http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/brokers/1/configs | jq -r ".data[range(0;10)].name"
```

... and let's get the next 10 items:

```bash
curl -sS http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/brokers/1/configs | jq -r ".data[range(10;20)].name"
```

Another useful trick - this will give you a fairly easy way to look at whether you are relying on default configuration values for most of your settings.

Let's start with the `source` property:

```bash
curl -sS http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/brokers/1/configs | jq -r ".data[].source"
```

And we can get a list of all the unique source types by running something like this:

```bash
curl -sS http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/brokers/1/configs | jq -r ".data[].source" | sort | uniq
```

Also note that there is an `is_default` property; let's take a quick look at the output for this:

```bash
curl -sS http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/brokers/1/configs | jq -r ".data[].is_default"
```

This should return a large number of `true` and a few `false` vales.

Let's say that we're not interested in the items that are set to the default values - but we are interested in those values that have been changed; maybe we're doing some troubleshooting between a few different environments and we suspect that configuration settings have been modified manually.

To start with, let's get all the items that have an `is_default` value of `false` (not defaults):

```bash
curl -sS http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/brokers/1/configs | jq -r '.data[] | select(.is_default == false)'
```

And we can reduce that down to a list of properties by adding `.name`:

```bash
curl -sS http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/brokers/1/configs | jq -r '.data[] | select(.is_default == false).name'
```

## Modifying the Configuration

Ok, we've spent a lot of time looking at things; now let's start making some configuration changes.

### Increasing the `max.message.bytes` setting

So we've been developing our application for a while now and we've hit a bit of an issue - our Producers have continually failed to write messages and they're receiving `RecordTooLargeException` exceptions.  

We know from querying ReST Proxy that the current setting is `1048588` which is just above 1MB and we know it's still set to the broker default.  

```bash
curl -sS http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/brokers/1/configs/message.max.bytes | jq
```

However some of our records are between 1.2MB and 1.3MB in size.  As such, we've decided to add a further 512K to the maximum size and we're going to apply this setting at the broker level as a new default for all topics (remember that you can also set this on a per-topic basis).

So we've agreed that we're going to increase this setting by a further `524294` bytes, giving us a new total of `1572882`; let's construct a payload to make that change. We need a few things for this:

1. It's an HTTP `PUT` request
2. The endpoint will be `http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/brokers/1/configs/message.max.bytes`
3. We're sending a JSON payload, so we'll use the `Content-Type: application/json` header

Our request body will contain the modified value:

```json
{
    "value": "1572882"
}
```

So our cURL request should look something like this:

```bash
curl -X PUT -H "Content-Type: application/json" --data '{"value": "1572882"}' "http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/brokers/1/configs/message.max.bytes"
```

You'll note that we get seemingly no response back from the server; it's returned an HTTP `204` status code (`No Content`), which means the change has worked; if it had failed, you would see perhaps a `400 (Bad Request)`, `401 (Unauthorized)`, a `403 (Forbidden)` or even a `5xx` exception if there was something like a retriable error or a schema related error.

If you want to confirm that you did get a `204` code back from ReST Proxy as a result of this call, you can run this instead:

```bash
curl -v -X PUT -H "Content-Type: application/json" --data '{"value": "1572882"}' "http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/brokers/1/configs/message.max.bytes"
```

Ok, so we've made a change, let's review the setting now:

```bash
curl -sS http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/brokers/1/configs/message.max.bytes | jq
```

Now we see that this is listed as a `DYNAMIC_BROKER_CONFIG`.  This means that the broker has taken the configuration value and added it dynamically - the new setting will be honoured - but the default remains in place.  This has implications for any restarts.  

So the upside here is that this was easy to change - just a single HTTP PUT request.  We can also easily see that the setting is no longer the default (`"is_default": false`) and we see the setting now has two synonym properties; we see the original and we see the updated setting.

So let's say that we had a meeting with the same development team who needed to send records that exceed the default 1MB and it began to dawn on them that there was a better way to model their data - and by doing this, they can make the messages much smaller without losing any fidelity.  

As a result of this, we no longer need the increased setting - let's delete it now!

```bash
curl -X DELETE http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/brokers/1/configs/message.max.bytes
```

If we now run our `HTTP GET` request, we now see our overridden (dynamic) setting is gone and all we have is the original 1MB default.

What I really like about this approach - if you have ReST Proxy available for a CP cluster - you can use the API to programmatically make decisions; I hope you can envisage a situation as I have where you can use this API to respond in your own application to some common Producer issues.  

Now obviously, this kind of flexibility is open to abuse (imagine a situation where one tenant application dynamically jacks up a lot of settings to way higher than they should be) - but I hope you can see that having the ability to make dynamic changes just from within an HTTP client is a hugely powerful tool that you have at your disposal.

## Creating a topic

So we've looked at working with the configuration at broker level - what about other common operations such as creating topics?

The simple invocation for creating a topic is:

1. To use an `HTTP POST` request
2. The endpoint will be http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/topics
3. We're sending a JSON payload, so we'll use the `Content-Type: application/json` header
4. We will receive JSON as a response to our request, so we'll set the `Accept: application/json` header

Our request body will contain the most minimal setting possible for creating the topic - the name:

```json
{
    "topic_name": "test-topic"
}
```

Putting that all together, we have:

```bash
curl -sS -X POST -H "Content-Type: application/json" -H "Accept: application/json" --data '{"topic_name": "test-topic"}' "http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/topics" | jq
```

A more full configuration might look like this:

```json
{
    "topic_name": "test-topic3",
    "partitions_count": 1, 
    "replication_factor": 1,
    "configs" : [
        {
            "name": "compression.type",
            "value": "gzip"
        },
        {
            "name": "min.insync.replicas",
            "value": 1
        }        
    ]
}
```

As a cURL one-liner:

```bash
curl -sS -X POST -H "Content-Type: application/json" -H "Accept: application/json" --data '{"topic_name": "test-topic3", "partitions_count": 1, "replication_factor": 1, "configs" : [{ "name": "compression.type", "value": "gzip"},{"name": "min.insync.replicas","value": 1}]}' "http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/topics" | jq
```

And to view the configuration after having created the topic:

```bash
curl -sS http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/topics/test-topic3/configs | jq
```

And as before, we can get a list of configuration names for the topic:

```bash
curl -sS http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/topics/test-topic3/configs | jq -r ".data[].name"
```


### Create multiple topics

Let's provision a few topics within a loop (note that there are many ways to do this - this example is a simple bash shell loop):

```bash
for topicName in a b c d e f g h i j k l m n o p q r s t u v w x y z; do
  curl -sS -X POST -H "Content-Type: application/json" -H "Accept: application/json" --data '{"topic_name": "topic_'$topicName'", "partitions_count": 1, "replication_factor": 1}' "http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/topics" | jq
done 
```

## Listing topics

Now let's get a list of all the topics (as JSON):

```bash
curl -sS http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/topics | jq
```

To get a list of all topic names:

```bash
curl -sS http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/topics | jq -r ".data[].topic_name"
```

## Finding single points of failure in topic configurations

You'd honestly be surprised at the number of support incidents that get created due to topics that are configured without High Availability in mind (`"replication_factor": 3` and `"min.insync.replicas" : 2`), so can we use ReST Proxy to identify those?

```bash
curl -sS http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/topics | jq -r ".data[] | select(.replication_factor == 1).topic_name"
```

Hopefully this gives you some ideas around how you can use ReST Proxy to manage topics.  As a note, you can also do the same work with ACLs - and we can review how these are handled in a future session if there is interest in doing so.

## Listing Topic Partitions

We can review partition data by running:

```bash
curl -sS "http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/topics/test-topic/partitions" | jq
```

## Producing to a topic

So we've created and explored topics; let's write some data using the Produce API.  We can start with the simplest invocation:

```bash
curl -sS -X POST -H "Content-Type: application/json" --data '{"value": {"type": "JSON", "data": {"one": "two", "three": "four"}}}' "http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/topics/test-topic/records" | jq 
```

You'll see that a JSON response is returned:

```json
{
  "error_code": 200,
  "cluster_id": "ZWe3nnZwTrKSM0aM2doAxQ",
  "topic_name": "test-topic",
  "partition_id": 0,
  "offset": 3,
  "timestamp": "2024-07-25T08:54:41.903Z",
  "value": {
    "type": "JSON",
    "size": 28
  }
}
```

We can use `jq` to filter for specifics in the resultset as the HTTP response is returned to our client.

Here we will just return the value of the `error_code` property:

```bash
curl -sS -X POST -H "Content-Type: application/json" --data '{"value": {"type": "JSON", "data": {"one": "two", "three": "four"}}}' "http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/topics/test-topic/records" | jq -r ".error_code"
```

And here we can get the offset:

```bash
curl -sS -X POST -H "Content-Type: application/json" --data '{"value": {"type": "JSON", "data": {"one": "two", "three": "four"}}}' "http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/topics/test-topic/records" | jq -r ".offset"
```

We should be able to only return the HTTP Error Code if it's not `200`:

```bash
curl -sS -m 5 -X POST -H "Content-Type: application/json" --data '{"value": {"type": "JSON", "data": {"one": "two", "three": "four"}}}' "http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/topics/test-topic/records" | jq -r ". | select(.error_code == 200 | not)"
```

For now I can confirm that the logic works by returning the JSON response if it's not 500 - and we can see the HTTP response does come back to the Client:

```bash
curl -sS -m 5 -X POST -H "Content-Type: application/json" --data '{"value": {"type": "JSON", "data": {"one": "two", "three": "four"}}}' "http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/topics/test-topic/records" | jq -r ". | select(.error_code == 500 | not)"
```

Fail faster with `-m nn`:

```bash
curl -sS -m 5 -X POST -H "Content-Type: application/json" --data '{"value": {"type": "JSON", "data": {"one": "two", "three": "four"}}}' "http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/topics/test-topic/records" | jq -r ". | select(.error_code == 200 | not)"
```

That's just producing single records though - the main advantage of the `/records` endpoint is that it allows you to stream records to it.  And this endpoint allows you to do this.  

However, cURL doesn't allow us to demo this easily, unfortunately, so we'll need to set up a demo using something like the Java HTTP Client - and to do this, we would use the `"Transfer-Encoding: chunked"` header.  This could be something for a future session.

As a note: you can pass in the header for streaming mode like this:

```bash
curl -sS -H "Transfer-Encoding: chunked" --data @file http://example.com
```

### Producing data using the `/v2/` endpoint

We can use the v2 endpoint to send batches using cURL - in this example, we're sending 3 messages to a topic called `testtopic`:

```bash
curl -X POST \
     -H "Content-Type: application/vnd.kafka.json.v2+json" \
     -H "Accept: application/vnd.kafka.v2+json" \
     --data '{"records":[
{"key":"alice","value":{"count":0}},
{"key":"alice","value":{"count":1}},
{"key":"alice","value":{"count":2}}
]}' \
     "http://localhost:8082/topics/testtopic" | jq .
```

The response will give us an Array containing offset information for each Record produced.

## Consumer Groups

Let's start by listing Consumer Groups:

```bash
curl -sS  http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/consumer-groups | jq
```

Note that Consumer Groups will be cleaned up if inactivity exceeds `consumer.instance.timeout.ms` (5 min default)

For now this will return an empty set.

TODO - get a Consumer Group
GET /clusters/ZWe3nnZwTrKSM0aM2doAxQ/consumer-groups/{consumer_group_id}

TODO - get consumers of a consumer group
GET /clusters/ZWe3nnZwTrKSM0aM2doAxQ/consumer-groups/{consumer_group_id}

TODO - get Consumer lag
GET /clusters/ZWe3nnZwTrKSM0aM2doAxQ/consumer-groups/{consumer_group_id}/lag-summary

TODO - get Assignments
GET /clusters/ZWe3nnZwTrKSM0aM2doAxQ/consumer-groups/{consumer_group_id}/consumers/{consumer_id}/assignments HTTP/1.1
Host: example.com

## Consuming from a topic

Now the first point of note is that there is a `v3` Consumer API planned - however as yet, it's not documented and there are no specifics that we can share at this point.  So for this section, we will use the `v2` endpoints to demonstrate the Consumer API from the ReST Proxy.

This is a three stage process

- Header `"Content-Type: application/vnd.kafka.v2+json"`
- Consumer Group: `"consumer_group_1"`
- Consumer Instance (the Consumer) `"consumer_instance_1"`

The initial payload will create the Consumer Instance and provide some standard information (such as `"auto.offset.reset"` and the data format and so on):  

```json
{   
    "name": "consumer_instance_1", 
    "format": "json", 
    "auto.offset.reset": "earliest"
}
```

### Step One: Register

We initiate an HTTP POST to `/consumers/{groupname}`

```bash
curl -sS -X POST -H "Content-Type: application/vnd.kafka.v2+json" --data '{"name": "my_consumer", "format": "binary", "auto.offset.reset": "earliest", "auto.commit.enable": "false"}' http://localhost:8082/consumers/consumer_group | jq
```

Returns some JSON


OLDER: Let's register our Consumer Instance and associate it with a Consumer Group:

```bash
curl -sS -X POST -H "Content-Type: application/vnd.kafka.v2+json" --data '{"name": "consumer_instance", "format": "json", "auto.offset.reset": "earliest"}' http://localhost:8082/consumers/consumer_group | jq
```

We will see a JSON response:

```json
{
  "instance_id": "consumer_instance",
  "base_uri": "http://localhost:8082/consumers/consumer_group/instances/consumer_instance"
}
```

### Step Two: Subscribe

curl -sS -X POST -H "Content-Type: application/vnd.kafka.v2+json" --data '{"topics":["topic_a"]}' http://localhost:8082/consumers/consumer_group/instances/my_consumer/subscription | jq

Not sure whether this worked ^


OLDER:

```bash
curl -sS -X POST -H "Content-Type: application/vnd.kafka.v2+json" --data '{"topics":["test-topic"]}' http://localhost:8082/consumers/consumer_group/instances/consumer_instance/subscription | jq
```

curl -sS -X POST -H "Content-Type: application/vnd.kafka.v2+json" --data '{"topics":["topic_a"]}' http://localhost:8082/consumers/consumer_group/instances/consumer_instance/subscription | jq


This step will return an `HTTP 204 (No Content)`; so if you see nothing, you know that the call has worked successfully.  If I were to modify the URL to subscribe a consumer instance with a different name, I'll see ReST Proxy returning an `HTTP 404` and some JSON:

```json
{
  "error_code": 40403,
  "message": "Consumer instance not found."
}
```

### Step Three: `GET` Records

TODO - There are still some issues with getting the Consumer group pieces working...

```bash
curl -sS -X GET -H "Accept: application/vnd.kafka.json.v2+json" http://localhost:8082/consumers/consumer_group/instances/consumer_instance/records
```

```bash
curl -X GET \
     -H "Accept: application/vnd.kafka.json.v2+json" \
     http://localhost:8082/consumers/consumer_group/instances/consumer_instance/records | jq .
```


## Further reading

- <https://docs.confluent.io/platform/current/kafka-rest/api.html#records-v3>
- <https://docs.confluent.io/platform/current/kafka-rest/api.html#configs-v3>
- <https://docs.confluent.io/platform/current/kafka-rest/api.html#topic-v3>
- <https://docs.confluent.io/platform/current/kafka-rest/api.html#partition-v3>
- <https://docs.confluent.io/platform/current/kafka-rest/api.html#accesslists>
- <https://docs.confluent.io/platform/current/kafka-rest/api.html#content-types>

------------------- TO DELETE BELOW

curl -sS http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/brokers/configs/message.max.bytes | jq

```bash
curl -X PUT -H "Content-Type: application/json" -H "Accept: application/json" --data '{"value": "1572882"}' "http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/brokers/1/configs/message.max.bytes"
```

curl -X POST \
     -H "Content-Type: application/vnd.kafka.json.v2+json" \
     -H "Accept: application/vnd.kafka.v2+json" \
     --data '{"records":[
{"key":"alice","value":{"count":0}},
{"key":"alice","value":{"count":1}},
{"key":"alice","value":{"count":2}}
]}' \
     "http://localhost:8082/topics/test-topic" | jq .



curl -sS http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/topics | jq -r ".data[].error_code"


-r ".data[] | select(.replication_factor == 1).topic_name"


curl -sS http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/brokers/1/configs | jq -r 'map(
  select(.is_default | not) | .value) | unique | .[]'

   
curl -sS http://localhost:8082/v3/clusters/ZWe3nnZwTrKSM0aM2doAxQ/brokers/1/configs | jq -r '.data[] | select(.is_default == false).name'


useful example, in particular for mac brew users:

List all bottled formulae
by querying the JSON and parsing the output

brew info --json=v1 --installed | jq -r 'map(
    select(.installed[].poured_from_bottle)|.name) | unique | .[]' | tr '\n' ' '
List all non-bottled formulae
by querying the JSON and parsing the output and using | not

brew info --json=v1 --installed | jq -r 'map(                                                                                                                          
  select(.installed[].poured_from_bottle | not) | .name) | unique | .[]'
Share
