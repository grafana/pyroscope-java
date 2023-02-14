/*
 * Copyright (C) 2014 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.pyroscope.javaagent.util.zip;

import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import kotlin.jvm.internal.Intrinsics;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@SuppressWarnings("KotlinInternalInJava")
public final class GzipSink implements Sink {
    private final BufferedSink sink;
    @NotNull
    private final Deflater deflater;
    private final DeflaterSink deflaterSink;
    private boolean closed;
    private final CRC32 crc;


    public GzipSink(@NotNull Sink sink, int compressionLevel) {
        Intrinsics.checkNotNullParameter(sink, "sink");
        this.sink = Okio.buffer(sink);
        this.deflater = new Deflater(compressionLevel, true);
        this.deflaterSink = new DeflaterSink((BufferedSink) this.sink, this.deflater);
        this.crc = new CRC32();

        // Write the Gzip header directly into the buffer for the sink to avoid handling IOException.
        Buffer buf = this.sink.getBuffer();
        buf.writeShort(0x1f8b); // Two-byte Gzip ID.
        buf.writeByte(8); // 8 == Deflate compression method.
        buf.writeByte(0); // No flags.
        buf.writeInt(0); // No modification time.
        buf.writeByte(0); // No extra flags.
        buf.writeByte(0); // No OS.
    }

    @Override
    public void write(@NotNull Buffer source, long byteCount) throws IOException {
//        require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
        if (!(byteCount >= 0L)) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }
        if (byteCount == 0L) return;
        updateCrc(source, byteCount);
        deflaterSink.write(source, byteCount);
    }


    @Override
    public void close() throws IOException {
        throw new IllegalStateException("should not be called. use end");
    }

    // almost same as okio.GzipSink.close but does sink.flush instead of sink.close
    public void end() throws IOException {
        if (!this.closed) {
            Throwable thrown = null;

            try {
                this.deflaterSink.finishDeflate$okio();
                this.writeFooter();
            } catch (Throwable var3) {
                thrown = var3;
            }

            try {
                this.deflater.end();
            } catch (Throwable var5) {
                if (thrown == null) {
                    thrown = var5;
                }
            }

            try {
                this.sink.flush();
            } catch (Throwable var4) {
                if (thrown == null) {
                    thrown = var4;
                }
            }

            this.closed = true;
            if (thrown != null) {
                Util.sneakyRethrow(thrown);
            }
        }
    }

    @Override
    public void flush() throws IOException {
        deflaterSink.flush();
    }

    @NotNull
    @Override
    public Timeout timeout() {
        return sink.timeout();
    }

    private void updateCrc(Buffer buffer, long byteCount) {
        Segment head = buffer.head;
        Intrinsics.checkNotNull(head);
        long remaining = byteCount;
        while (remaining > 0) {
            int segmentLength = (int) Math.min(remaining, head.limit - head.pos);
            this.crc.update(head.data, head.pos, segmentLength);
            remaining -= segmentLength;
            head = head.next;
            Intrinsics.checkNotNull(head);
        }
    }

    private final void writeFooter() throws IOException {
        this.sink.writeIntLe((int) this.crc.getValue());
        this.sink.writeIntLe((int) this.deflater.getBytesRead());
    }


    /**
     * <a href="https://github.com/square/okhttp/blob/64a9c8e4db394097fe6150915fcea7a7f11572a9/okhttp/src/jvmMain/kotlin/okhttp3/RequestBody.kt#L184">origin</a>
     * Returns a gzip version of the RequestBody, with compressed payload.
     * This is not automatic as not all servers support gzip compressed requests.
     * <p>
     * ```
     * val request = Request.Builder().url("...")
     * .addHeader("Content-Encoding", "gzip")
     * .post(uncompressedBody.gzip())
     * .build()
     * ```
     */
    public static RequestBody gzip(RequestBody req, int compressionLevel) {
        return new RequestBody() {
            @Nullable
            @Override
            public MediaType contentType() {
                return req.contentType();
            }

            @Override
            public long contentLength() throws IOException {
                return -1;// We don't know the compressed length in advance!
            }

            @Override
            public void writeTo(@NotNull BufferedSink sink) throws IOException {
                GzipSink gzipSink = new GzipSink(sink, compressionLevel);
                BufferedSink buffer = Okio.buffer(gzipSink);
                req.writeTo(buffer);
                // do not close gzipSink & buffer to avoid closing upstream sink
                // do flushes instead
                buffer.flush();
                gzipSink.end();
            }

            @Override
            public boolean isOneShot() {
                return req.isOneShot();
            }
        };
    }

}
