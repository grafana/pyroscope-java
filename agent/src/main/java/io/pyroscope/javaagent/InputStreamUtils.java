package io.pyroscope.javaagent;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

class InputStreamUtils {
    static String readToString(final InputStream is) throws IOException {
        try (final BufferedInputStream bais = new BufferedInputStream(Objects.requireNonNull(is));
             final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            final byte[] buf = new byte[8192];
            int read;
            while ((read = bais.read(buf)) > 0) {
                baos.write(buf, 0, read);
            }
            bais.close();
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
