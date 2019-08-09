package io.helidon.examples.quickstart.se;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Flow.Publisher;
import io.helidon.media.common.PublisherInputStream;
import io.helidon.webserver.BadRequestException;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.jvnet.mimepull.MIMEMessage;
import org.jvnet.mimepull.MIMEPart;

public class FileUploadService implements Service {

    private final Executor executor = Executors.newFixedThreadPool(4);

    @Override
    public void update(Routing.Rules rules) {
        rules.post(this::uploadHandler);
    }

    private Map<String, String> partsAsString(Publisher<DataChunk> publisher, String boundary) throws IOException {
        InputStream is = new PublisherInputStream(publisher);
        MIMEMessage mimeMessage = new MIMEMessage(is, boundary);
        Map<String, String> parts = new HashMap<>();
        for (MIMEPart part : mimeMessage.getAttachments()) {
            List<String> contentDisposition = part.getHeader("Content-Disposition");
            if (contentDisposition == null || contentDisposition.isEmpty()) {
                continue;
            }
            String partName = contentDisposition.get(0)
                    .replaceFirst("(?i)^.*name=\"?([^\"]+)\"?.*$", "$1");

            // NOT safe !
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(part.read()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }
            parts.put(partName, sb.toString());
        }
        return parts;
    }

    private void uploadHandler(ServerRequest request, ServerResponse response) {
        MediaType contentType = request.headers().contentType()
                .filter(MediaType.MULTIPART_FORM_DATA::test)
                .orElseThrow(() -> new BadRequestException("Invalid Content-Type"));
        String boundary = contentType.parameters().get("boundary");
        if (boundary == null) {
            throw new BadRequestException("Missing boundary parameter");
        }
        executor.execute(() -> {
            try {
                Map<String, String> parts = partsAsString(request.content(), boundary);
                System.out.println(parts);
                response.send("uploaded!");
            } catch (IOException ex) {
                request.next(ex);
            }
        });
    }
}
