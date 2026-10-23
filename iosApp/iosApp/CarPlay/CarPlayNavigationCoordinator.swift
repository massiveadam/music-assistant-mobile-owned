import Foundation

/// Completion used by the CarPlay navigation driver. CarPlay reports both a
/// success flag and an optional error for template mutations.
typealias CarPlayNavigationCompletion = (Bool, Error?) -> Void

/// The small interface required by `CarPlayNavigationCoordinator`. Keeping
/// CarPlay itself out of the coordinator makes stack-limit behavior
/// deterministic to test without creating UIKit/CarPlay objects.
protocol CarPlayNavigationDriver: AnyObject {
    associatedtype Template: AnyObject

    var templates: [Template] { get }

    func setRootTemplate(
        _ template: Template,
        animated: Bool,
        completion: @escaping CarPlayNavigationCompletion
    )

    func pushTemplate(
        _ template: Template,
        animated: Bool,
        completion: @escaping CarPlayNavigationCompletion
    )

    func popToRootTemplate(
        animated: Bool,
        completion: @escaping CarPlayNavigationCompletion
    )

    func pop(
        to template: Template,
        animated: Bool,
        completion: @escaping CarPlayNavigationCompletion
    )
}

/// CarPlay raises an Objective-C exception when its five-template audio limit is exceeded.
/// Serialize mutations and re-check depth immediately before each one.
final class CarPlayNavigationCoordinator<Driver: CarPlayNavigationDriver> {
    typealias Template = Driver.Template

    private enum Request {
        case setRoot(template: Template, animated: Bool)
        case push(template: Template, animated: Bool)
        case singleton(template: Template, animated: Bool)
    }

    private let driver: Driver
    private let maximumDepth: Int
    private let onFailure: (Error?) -> Void
    private var operationInFlight = false
    private var sessionIsValid = false
    private var generation: UInt = 0

    init(
        driver: Driver,
        maximumDepth: Int = 5,
        onFailure: @escaping (Error?) -> Void = { _ in }
    ) {
        dispatchPrecondition(condition: .onQueue(.main))
        precondition(maximumDepth > 0)
        self.driver = driver
        self.maximumDepth = maximumDepth
        self.onFailure = onFailure
    }

    /// Starts a new CarPlay connection session and invalidates callbacks from
    /// every prior session. This must be called before root setup.
    func startSession() {
        dispatchPrecondition(condition: .onQueue(.main))
        generation &+= 1
        operationInFlight = false
        sessionIsValid = true
    }

    /// Invalidates all work and completion closures belonging to the disconnected session.
    func invalidateSession() {
        dispatchPrecondition(condition: .onQueue(.main))
        generation &+= 1
        operationInFlight = false
        sessionIsValid = false
    }

    func setRootTemplate(_ template: Template, animated: Bool) {
        dispatchPrecondition(condition: .onQueue(.main))
        enqueue(.setRoot(template: template, animated: animated))
    }

    func pushTemplate(_ template: Template, animated: Bool) {
        dispatchPrecondition(condition: .onQueue(.main))
        enqueue(.push(template: template, animated: animated))
    }

    /// Navigates to a singleton template (for example
    /// `CPNowPlayingTemplate.shared`). If it is already in the stack, the
    /// existing instance is reused by popping to it instead of pushing it.
    func showSingletonTemplate(_ template: Template, animated: Bool) {
        dispatchPrecondition(condition: .onQueue(.main))
        enqueue(.singleton(template: template, animated: animated))
    }

    private func enqueue(_ request: Request) {
        dispatchPrecondition(condition: .onQueue(.main))
        guard sessionIsValid, !operationInFlight else { return }
        generation &+= 1
        operationInFlight = true
        perform(request, generation: generation)
    }

    private func isCurrent(_ mutation: UInt) -> Bool {
        dispatchPrecondition(condition: .onQueue(.main))
        return sessionIsValid && operationInFlight && generation == mutation
    }

    private func finish(
        mutation: UInt,
        success: Bool,
        error: Error? = nil
    ) {
        dispatchPrecondition(condition: .onQueue(.main))
        guard isCurrent(mutation) else { return }
        operationInFlight = false
        if !success {
            onFailure(error)
        }
    }

    private func perform(_ request: Request, generation: UInt) {
        dispatchPrecondition(condition: .onQueue(.main))
        switch request {
        case let .setRoot(template, animated):
            driver.setRootTemplate(template, animated: animated) { [weak self] success, error in
                dispatchPrecondition(condition: .onQueue(.main))
                self?.finish(mutation: generation, success: success, error: error)
            }

        case let .push(template, animated):
            performPush(template, animated: animated, reuseExisting: false, generation: generation)

        case let .singleton(template, animated):
            performPush(template, animated: animated, reuseExisting: true, generation: generation)
        }
    }

    private func performPush(
        _ template: Template,
        animated: Bool,
        reuseExisting: Bool,
        generation: UInt
    ) {
        dispatchPrecondition(condition: .onQueue(.main))
        guard isCurrent(generation) else { return }

        // Re-check immediately before every mutation. A completion or another
        // framework callback may have changed the stack since enqueue time.
        let stack = driver.templates
        if reuseExisting, let existing = stack.first(where: { $0 === template }) {
            if stack.last === existing {
                finish(mutation: generation, success: true)
            } else {
                let mutation = generation
                driver.pop(to: existing, animated: animated) { [weak self] success, error in
                    dispatchPrecondition(condition: .onQueue(.main))
                    self?.finish(mutation: mutation, success: success, error: error)
                }
            }
            return
        }

        guard stack.count < maximumDepth else {
            // Reset first, but never assume that a callback means the reset succeeded.
            let mutation = generation
            driver.popToRootTemplate(animated: false) { [weak self] success, error in
                dispatchPrecondition(condition: .onQueue(.main))
                guard let self, self.isCurrent(mutation) else { return }
                guard success else {
                    self.finish(mutation: mutation, success: false, error: error)
                    return
                }

                // Measure again after reset before issuing the push.
                guard self.driver.templates.count < self.maximumDepth else {
                    self.finish(mutation: mutation, success: false)
                    return
                }
                self.performDirectPush(template, animated: animated)
            }
            return
        }

        performDirectPush(template, animated: animated)
    }

    private func performDirectPush(_ template: Template, animated: Bool) {
        dispatchPrecondition(condition: .onQueue(.main))
        guard sessionIsValid, operationInFlight,
              driver.templates.count < maximumDepth else {
            finish(mutation: generation, success: false)
            return
        }
        let mutation = generation
        driver.pushTemplate(template, animated: animated) { [weak self] success, error in
            dispatchPrecondition(condition: .onQueue(.main))
            self?.finish(mutation: mutation, success: success, error: error)
        }
    }
}
