// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "MacLink",
    platforms: [.macOS(.v13)],
    products: [
        .executable(name: "MacLink", targets: ["MacLink"])
    ],
    dependencies: [
        .package(url: "https://github.com/apple/swift-protobuf.git", from: "1.25.0"),
        .package(url: "https://github.com/daltoniam/Starscream.git", from: "4.0.0")
    ],
    targets: [
        .executableTarget(
            name: "MacLink",
            dependencies: [
                .product(name: "SwiftProtobuf", package: "swift-protobuf")
            ],
            path: "MacLink/Sources"
        )
    ]
)
