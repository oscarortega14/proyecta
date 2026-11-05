import SwiftUI
import SelahRemotoUI

// Toda la app está en Kotlin (módulo remoteUi, Compose Multiplatform). Aquí solo va la ventana.
@main
struct SelahRemotoApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea() // Compose deja libre la isla y la barra de abajo por su cuenta
                .preferredColorScheme(.dark)
                // selah://connect?host=… (botón "Abrir en la app" del control web o QR)
                .onOpenURL { url in IosEntryKt.openUrl(url: url.absoluteString) }
                // En pleno culto la pantalla no debe apagarse.
                .onAppear { UIApplication.shared.isIdleTimerDisabled = true }
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        IosEntryKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
