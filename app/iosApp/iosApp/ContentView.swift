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
            // ignoresSafeArea() with no arguments, deliberately: every edge, every region.
            //
            // Restricting it to .keyboard let SwiftUI inset the Compose view by the top and
            // bottom safe areas, and the window's own background showed through as white bands
            // above the status bar and below the home indicator -- against a #141311 app.
            //
            // Compose is the right place to apply these insets, not SwiftUI: App.kt already
            // calls safeContentPadding(), so content stays clear of the notch while the
            // background paints edge to edge. Handing the job to SwiftUI instead means the
            // background stops at the inset, which is the bug.
            .ignoresSafeArea()
    }
}
