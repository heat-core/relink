import Foundation
import XCTest
@testable import TetherCore

final class LanLinkTests: XCTestCase {
    func testOfferAcceptsOnlyCanonicalPrivateIPv4AndAValidPort() throws {
        func decode(_ ip: String, _ port: Int) throws -> LanEndpoint {
            try JSONDecoder().decode(LanEndpoint.self, from: Data(#"{"ip":"\#(ip)","port":\#(port)}"#.utf8))
        }
        XCTAssertEqual(try decode("192.168.20.1", 24783).port, 24783)
        for host in ["127.0.0.1", "203.0.113.2", "1.1.1.1", "172.32.0.1", "10.0..1", "10.00.0.1", "host.local", "::1"] {
            XCTAssertThrowsError(try decode(host, 24783), host)
        }
        XCTAssertThrowsError(try decode("192.168.20.1", 0))
        XCTAssertThrowsError(try decode("192.168.20.1", 65536))
    }

    func testBluetoothOnlyAndBoundedReconnectBackoff() {
        XCTAssertNil(LinkConnectionMode.bluetoothOnly.upgradeDelay)
        XCTAssertGreaterThan(LinkConnectionMode.automatic.upgradeDelay!, LinkConnectionMode.wifiPreferred.upgradeDelay!)
        var retry = LanRetry()
        XCTAssertEqual((0..<8).map { _ in retry.failed(jitter: 0.5) }, [4.5, 8.5, 16.5, 30.5, 30.5, 30.5, 30.5, 30.5])
        retry.reset()
        XCTAssertEqual(retry.failed(jitter: 0), 4)
    }
}
