package io.helidon.examples.quickstart.se;

import io.helidon.common.OptionalHelper;
import io.helidon.common.http.MediaType;
import io.helidon.webserver.BadRequestException;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.jvnet.mimepull.MIMEMessage;
import org.jvnet.mimepull.MIMEPart;

public class FileUploadService implements Service {

    @Override
    public void update(Routing.Rules rules) {
        rules.post(this::uploadHandler);
    }

    private void dumpParts(MIMEMessage mimeMessage) throws IOException {
        for (MIMEPart part : mimeMessage.getAttachments()) {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(part.read()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    System.out.println(line);
                }
            }
        }
    }

    private void handleMultiPart(ServerRequest request, String boundary) {
        request.content().as(InputStream.class).thenAcceptAsync(is -> {
            MIMEMessage mimeMessage = new MIMEMessage(is, boundary);
            try {
                dumpParts(mimeMessage);
            } catch (IOException ex) {
                request.next(ex);
            }
        });
    }

    private void uploadHandler(ServerRequest request, ServerResponse response) {
        OptionalHelper.from(request.headers().contentType())
                .ifPresentOrElse(contentType -> {
                    if (!MediaType.MULTIPART_FORM_DATA.test(contentType)) {
                        request.next(new BadRequestException(
                                "Not a multipart request"));
                        return;
                    }
                    String boundary = contentType.parameters().get("boundary");
                    if (boundary == null) {
                        request.next(new BadRequestException(
                                "Missing boundary parameter"));
                        return;
                    }
                    handleMultiPart(request, boundary);
                }, () -> request.next(new BadRequestException(
                "Content-Type header required")));
    }
}
