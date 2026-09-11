package org.example.lab8;

import org.example.lab7.RowRange;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

final class DoublesFile {
    static final int CHUNK = 8192;
    static final ByteOrder ORDER = ByteOrder.BIG_ENDIAN;

    private DoublesFile() {
    }

    static long byteOffset(int elementIndex) {
        return (long) elementIndex * Double.BYTES;
    }

    static long matrixBytes(int n) {
        return byteOffset(n * n);
    }

    static void writeAll(Path path, double[] matrix) throws IOException {
        Files.createDirectories(path.getParent() == null ? Path.of(".") : path.getParent());
        try (FileChannel channel = FileChannel.open(
                path,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.WRITE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
        )) {
            write(channel, 0L, matrix, 0, matrix.length);
        }
    }

    static void createEmpty(Path path, int n) throws IOException {
        Files.createDirectories(path.getParent() == null ? Path.of(".") : path.getParent());
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
            file.setLength(matrixBytes(n));
        }
    }

    static double[] readRows(Path path, int n, RowRange range) throws IOException {
        double[] rows = new double[range.doubleCount(n)];
        if (range.isEmpty()) {
            return rows;
        }
        try (FileChannel channel = FileChannel.open(path, java.nio.file.StandardOpenOption.READ)) {
            read(channel, byteOffset(range.doubleOffset(n)), rows, 0, rows.length);
        }
        return rows;
    }

    static void writeRows(Path path, int n, RowRange range, double[] rows) throws IOException {
        if (range.isEmpty()) {
            return;
        }
        try (FileChannel channel = FileChannel.open(
                path,
                java.nio.file.StandardOpenOption.WRITE
        )) {
            write(channel, byteOffset(range.doubleOffset(n)), rows, 0, rows.length);
        }
    }

    static void write(FileChannel channel, long position, double[] src, int offset, int count)
            throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(CHUNK * Double.BYTES).order(ORDER);
        int done = 0;
        channel.position(position);
        while (done < count) {
            int batch = Math.min(CHUNK, count - done);
            fill(buffer, src, offset + done, batch);
            drain(channel, buffer);
            done += batch;
        }
    }

    static void read(FileChannel channel, long position, double[] dest, int offset, int count)
            throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(CHUNK * Double.BYTES).order(ORDER);
        int done = 0;
        channel.position(position);
        while (done < count) {
            int batch = Math.min(CHUNK, count - done);
            fillFromChannel(channel, buffer, batch);
            copyOut(buffer, dest, offset + done, batch);
            done += batch;
        }
    }

    static void fill(ByteBuffer buffer, double[] src, int offset, int count) {
        buffer.clear();
        for (int i = 0; i < count; i++) {
            buffer.putDouble(src[offset + i]);
        }
        buffer.flip();
    }

    static void drain(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    static void fillFromChannel(FileChannel channel, ByteBuffer buffer, int count) throws IOException {
        buffer.clear();
        buffer.limit(count * Double.BYTES);
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                throw new IOException("Неожиданный конец файла матрицы");
            }
        }
        buffer.flip();
    }

    static void copyOut(ByteBuffer buffer, double[] dest, int offset, int count) {
        for (int i = 0; i < count; i++) {
            dest[offset + i] = buffer.getDouble();
        }
    }
}
