import Foundation

/// Splits interleaved PCM into chunks that fit an AudioQueue buffer.
///
/// Expects whole-frame input; a short tail chunk is emitted as-is, not truncated.
enum PCMChunker {
    static func split(_ data: Data, frameSize: Int, capacity: Int) -> [Data] {
        precondition(frameSize > 0)
        precondition(capacity >= frameSize)

        guard !data.isEmpty else { return [] }

        let chunkCapacity = capacity - (capacity % frameSize)
        var chunks: [Data] = []
        chunks.reserveCapacity((data.count + chunkCapacity - 1) / chunkCapacity)

        var offset = 0
        while offset < data.count {
            let length = min(chunkCapacity, data.count - offset)
            chunks.append(data.subdata(in: offset..<(offset + length)))
            offset += length
        }
        return chunks
    }
}
