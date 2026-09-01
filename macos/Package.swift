// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "LeafBoardHub",
    platforms: [.macOS(.v14)],
    products: [
        .library(name: "LeafBoardCore", targets: ["LeafBoardCore"]),
        .executable(name: "LeafBoardHub", targets: ["LeafBoardHub"]),
        .executable(name: "LeafBoardCredentialHelper", targets: ["LeafBoardCredentialHelper"])
    ],
    targets: [
        .target(
            name: "LeafBoardCore",
            linkerSettings: [
                .linkedFramework("Network")
            ]
        ),
        .executableTarget(
            name: "LeafBoardHub",
            dependencies: ["LeafBoardCore"]
        ),
        .executableTarget(
            name: "LeafBoardCredentialHelper",
            linkerSettings: [.linkedFramework("Security")]
        ),
        .testTarget(
            name: "LeafBoardCoreTests",
            dependencies: ["LeafBoardCore"]
        )
    ]
)
