package app.xivgear.userstorage.compression;

public interface Compressor {

	byte[] compress(byte[] uncompressed);

	byte[] decompress(byte[] compressed);

}
