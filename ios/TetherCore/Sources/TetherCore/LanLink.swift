import Foundation

public enum LinkConnectionMode: String, CaseIterable, Hashable, Sendable {
    public static let preferenceKey = "relink.connection-mode"
    case automatic, wifiPreferred, bluetoothOnly

    var upgradeDelay: TimeInterval? {
        switch self {
        case .automatic: return 3
        case .wifiPreferred: return 1
        case .bluetoothOnly: return nil
        }
    }
}

public struct LanEndpoint: Codable, Equatable, Sendable {
    public let ip: String
    public let port: UInt16

    public init?(ip: String, port: UInt16) {
        let bytes = ip.split(separator: ".", omittingEmptySubsequences: false).compactMap { UInt8($0) }
        guard bytes.count == 4, bytes.map(String.init).joined(separator: ".") == ip, port != 0 else { return nil }
        guard bytes[0] == 10 || bytes[0] == 172 && (16...31).contains(bytes[1]) ||
            bytes[0] == 192 && bytes[1] == 168 else { return nil }
        self.ip = ip
        self.port = port
    }

    private enum CodingKeys: String, CodingKey { case ip, port }

    public init(from decoder: Decoder) throws {
        let fields = try decoder.container(keyedBy: CodingKeys.self)
        guard let endpoint = Self(ip: try fields.decode(String.self, forKey: .ip),
                                  port: try fields.decode(UInt16.self, forKey: .port)) else {
            throw DecodingError.dataCorruptedError(forKey: .ip, in: fields, debugDescription: "Invalid private IPv4 endpoint")
        }
        self = endpoint
    }
}

struct LanRetry {
    private(set) var failures = 0

    mutating func failed(jitter: Double) -> TimeInterval {
        failures = min(failures + 1, 5)
        return Double(min(30, 2 << failures)) + min(1, max(0, jitter))
    }

    mutating func reset() { failures = 0 }
}

#if canImport(Network) && canImport(CryptoKit)
import Network

/// A TCP candidate keeps its own record reader and keys until the paired host proves possession of its static key.
/// After authentication this pipe delivers ordered records on the link queue.
final class LanPipe {
    private let socket: NWConnection
    private var handshake: CentralHandshake?
    private var awaitingFinish: (SecureSession, SessionKeys)?
    private var reader = RecordReader()
    private var timeout: DispatchWorkItem?
    private var closed = false
    var onAuthenticated: ((SecureSession) -> Void)?
    var onRecord: ((Record, Bool) -> Void)?
    var onFailure: (() -> Void)?
    var onWritten: (() -> Void)?

    init?(endpoint: LanEndpoint, handshake: CentralHandshake) {
        guard let host = IPv4Address(endpoint.ip), let port = NWEndpoint.Port(rawValue: endpoint.port) else { return nil }
        let tcp = NWProtocolTCP.Options()
        tcp.noDelay = true
        let options = NWParameters(tls: nil, tcp: tcp)
        options.requiredInterfaceType = .wifi
        socket = NWConnection(host: .ipv4(host), port: port, using: options)
        self.handshake = handshake
    }

    func start(queue: DispatchQueue) {
        let deadline = DispatchWorkItem { [weak self] in self?.fail() }
        timeout = deadline
        queue.asyncAfter(deadline: .now() + 5, execute: deadline)
        socket.stateUpdateHandler = { [weak self] state in
            guard let self, !self.closed else { return }
            switch state {
            case .ready:
                if let handshake = self.handshake { self.write(handshake.hello().encoded()); self.receive() }
            case .failed, .cancelled:
                self.fail()
            default:
                break
            }
        }
        socket.start(queue: queue)
    }

    func write(_ bytes: Data, tracked: Bool = false) {
        socket.send(content: bytes, completion: .contentProcessed { [weak self] error in
            guard let self, !self.closed else { return }
            if error != nil { self.fail() }
            else if tracked { self.onWritten?() }
        })
    }

    private func receive() {
        socket.receive(minimumIncompleteLength: 1, maximumLength: 4096) { [weak self] data, _, complete, error in
            guard let self, !self.closed else { return }
            if let data {
                self.reader.append(data)
                do {
                    while !self.closed, let record = try self.reader.next() { try self.handle(record) }
                } catch {
                    self.fail()
                    return
                }
            }
            if complete || error != nil { self.fail() }
            else if !self.closed { self.receive() }
        }
    }

    private func handle(_ record: Record) throws {
        if let handshake {
            let (session, finish) = try handshake.receive(record)
            guard let keys = handshake.keys else { throw HandshakeError.finishMismatch }
            self.handshake = nil
            awaitingFinish = (session, keys)
            write(finish.encoded())
        } else if let (session, keys) = awaitingFinish {
            try CentralHandshake.verifyFinish(session, keys: keys, record: record)
            awaitingFinish = nil
            timeout?.cancel()
            onAuthenticated?(session)
        } else {
            onRecord?(record, reader.pendingBytes > 0)
        }
    }

    private func fail() {
        guard !closed else { return }
        close()
        onFailure?()
    }

    func close() {
        closed = true
        timeout?.cancel()
        socket.stateUpdateHandler = nil
        socket.cancel()
    }
}
#endif
