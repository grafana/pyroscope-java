package io.pyroscope.javaagent.impl;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * Streams a file to the request with positional reads, so the body can be written more than once
 * (retries, redirects) without loading the file into memory.
 */
final class FileChannelRequestBody extends RequestBody {
    private final FileChannel channel;
    @Nullable
    private final MediaType contentType;

    FileChannelRequestBody(@NotNull FileChannel channel, @Nullable MediaType contentType) {
        this.channel = channel;
        this.contentType = contentType;
    }

    @Nullable
    @Override
    public MediaType contentType() {
        return contentType;
    }

    @Override
    public long contentLength() throws IOException {
        return channel.size();
    }

    @Override
    public void writeTo(@NotNull BufferedSink sink) throws IOException {
        long size = channel.size();
        long position = 0;
        while (position < size) {
            long written = channel.transferTo(position, size - position, sink);
            if (written <= 0) {
                throw new EOFException("file truncated at " + position + " of " + size + " bytes");
            }
            position += written;
        }
    }
}
