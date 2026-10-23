import XCTest

final class PCMChunkerTests: XCTestCase {
    func testEmptyInputProducesNoChunks() {
        XCTAssertEqual(
            PCMChunker.split(Data(), frameSize: 8, capacity: 64),
            []
        )
    }

    func testInputWithinCapacityRemainsOneChunk() {
        let input = Data(0..<32)

        let chunks = PCMChunker.split(input, frameSize: 8, capacity: 64)

        XCTAssertEqual(chunks, [input])
    }

    func testExactCapacityRemainsOneChunk() {
        let input = Data(0..<64)

        let chunks = PCMChunker.split(input, frameSize: 8, capacity: 64)

        XCTAssertEqual(chunks, [input])
    }

    func testOversizedInputPreservesAllBytesInFrameAlignedChunks() {
        let input = Data((0..<200).map { UInt8($0) })

        let chunks = PCMChunker.split(input, frameSize: 8, capacity: 64)

        XCTAssertEqual(chunks.map(\.count), [64, 64, 64, 8])
        XCTAssertTrue(chunks.allSatisfy { $0.count <= 64 && $0.count % 8 == 0 })
        XCTAssertEqual(chunks.reduce(into: Data()) { $0.append($1) }, input)
    }

    func testCapacityIsReducedToTheLargestWholeFrame() {
        let input = Data((0..<42).map { UInt8($0) })

        let chunks = PCMChunker.split(input, frameSize: 6, capacity: 10)

        XCTAssertEqual(chunks.map(\.count), [6, 6, 6, 6, 6, 6, 6])
        XCTAssertTrue(chunks.allSatisfy { $0.count <= 10 && $0.count % 6 == 0 })
        XCTAssertEqual(chunks.reduce(into: Data()) { $0.append($1) }, input)
    }

    func testRealisticOversizedPcmBlockPreservesEveryByte() {
        let input = Data(repeating: 0xA5, count: 368_640)

        let chunks = PCMChunker.split(input, frameSize: 8, capacity: 65_536)

        XCTAssertEqual(chunks.map(\.count), [65_536, 65_536, 65_536, 65_536, 65_536, 40_960])
        XCTAssertEqual(chunks.reduce(0) { $0 + $1.count }, input.count)
        XCTAssertTrue(chunks.allSatisfy { $0.count <= 65_536 && $0.count % 8 == 0 })
    }
}
