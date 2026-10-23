import CarPlay

/// Adapter around CPInterfaceController. It is deliberately separate from the
/// state machine so the coordinator's stack behavior can be tested without
/// instantiating CarPlay templates.
final class CarPlayInterfaceControllerNavigationDriver: CarPlayNavigationDriver {
    typealias Template = CPTemplate

    weak var interfaceController: CPInterfaceController?

    init(interfaceController: CPInterfaceController) {
        self.interfaceController = interfaceController
    }

    var templates: [CPTemplate] {
        interfaceController?.templates ?? []
    }

    func setRootTemplate(
        _ template: CPTemplate,
        animated: Bool,
        completion: @escaping CarPlayNavigationCompletion
    ) {
        interfaceController?.setRootTemplate(template, animated: animated, completion: completion)
            ?? completion(false, nil)
    }

    func pushTemplate(
        _ template: CPTemplate,
        animated: Bool,
        completion: @escaping CarPlayNavigationCompletion
    ) {
        interfaceController?.pushTemplate(template, animated: animated, completion: completion)
            ?? completion(false, nil)
    }

    func popToRootTemplate(
        animated: Bool,
        completion: @escaping CarPlayNavigationCompletion
    ) {
        interfaceController?.popToRootTemplate(animated: animated, completion: completion)
            ?? completion(false, nil)
    }

    func pop(
        to template: CPTemplate,
        animated: Bool,
        completion: @escaping CarPlayNavigationCompletion
    ) {
        interfaceController?.pop(to: template, animated: animated, completion: completion)
            ?? completion(false, nil)
    }
}
