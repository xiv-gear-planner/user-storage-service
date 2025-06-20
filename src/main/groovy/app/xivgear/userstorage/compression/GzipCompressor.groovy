package app.xivgear.userstorage.compression

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Context

import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

@Context
@CompileStatic
class GzipCompressor implements Compressor {

	@Override
	byte[] compress(byte[] uncompressed) {
		ByteArrayOutputStream out = new ByteArrayOutputStream()
		try (GZIPOutputStream gzipStream = new GZIPOutputStream(out)) {
			gzipStream.write(uncompressed)
		}
		return out.toByteArray()
	}

	@Override
	byte[] decompress(byte[] compressed) {
		ByteArrayInputStream byteStream = new ByteArrayInputStream(compressed)
		try (GZIPInputStream gzipStream = new GZIPInputStream(byteStream)) {
			return gzipStream.readAllBytes()
		}
	}
}
