import SwiftUI
import ComposeApp

/// Hosts the Compose Multiplatform UI. MainViewController() comes from :composeApp's iosMain
/// source set, exported through the "ComposeApp" framework.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard) // Compose applies the keyboard inset itself.
    }
}
