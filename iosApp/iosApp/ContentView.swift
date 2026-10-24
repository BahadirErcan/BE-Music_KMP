import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        // Kotlin tarafındaki giriş noktası:
        // composeApp/src/iosMain/kotlin/com/be/music/MainViewController.kt
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.container, edges: .all)
    }
}

#Preview {
    ContentView()
}
