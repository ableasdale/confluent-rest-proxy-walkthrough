import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Consumer {
    public static void main(String[] args) throws URISyntaxException, IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://localhost:8082/consumers/cg1/instances/ci1/records"))
                .headers("Accept", "application/vnd.kafka.json.v2+json")
                .GET()
                .build();

        //-H "Accept: application/vnd.kafka.json.v2+json" \
        //     http://localhost:8082/consumers/cg1/instances/ci1/records


        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        HttpHeaders responseHeaders = response.headers();
        System.out.println(response.body());
    }
}
