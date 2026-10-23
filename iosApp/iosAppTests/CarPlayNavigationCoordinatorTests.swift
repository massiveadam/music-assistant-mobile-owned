import XCTest

@MainActor
final class CarPlayNavigationCoordinatorTests: XCTestCase {
    private final class Template {}

    private final class Driver: CarPlayNavigationDriver {
        enum Operation {
            case setRoot(Template, CarPlayNavigationCompletion)
            case push(Template, CarPlayNavigationCompletion)
            case popToRoot(CarPlayNavigationCompletion)
            case pop(Template, CarPlayNavigationCompletion)
        }

        var templates: [Template] = []
        var operations: [Operation] = []
        var maxConcurrentOperations = 0
        private var activeOperations = 0

        func setRootTemplate(
            _ template: Template,
            animated: Bool,
            completion: @escaping CarPlayNavigationCompletion
        ) {
            begin(.setRoot(template, completion))
        }

        func pushTemplate(
            _ template: Template,
            animated: Bool,
            completion: @escaping CarPlayNavigationCompletion
        ) {
            begin(.push(template, completion))
        }

        func popToRootTemplate(
            animated: Bool,
            completion: @escaping CarPlayNavigationCompletion
        ) {
            begin(.popToRoot(completion))
        }

        func pop(
            to template: Template,
            animated: Bool,
            completion: @escaping CarPlayNavigationCompletion
        ) {
            begin(.pop(template, completion))
        }

        func completeNext(success: Bool, mutateStack: Bool = true) {
            guard !operations.isEmpty else {
                XCTFail("Expected an outstanding operation")
                return
            }
            let operation = operations.removeFirst()
            activeOperations -= 1
            if success && mutateStack {
                switch operation {
                case let .setRoot(template, _): templates = [template]
                case let .push(template, _): templates.append(template)
                case .popToRoot: templates = Array(templates.prefix(1))
                case let .pop(template, _):
                    guard let index = templates.firstIndex(where: { $0 === template }) else {
                        XCTFail("Template was not in stack")
                        break
                    }
                    templates = Array(templates.prefix(index + 1))
                }
            }
            switch operation {
            case let .setRoot(_, completion), let .push(_, completion),
                 let .popToRoot(completion), let .pop(_, completion):
                completion(success, nil)
            }
        }

        private func begin(_ operation: Operation) {
            activeOperations += 1
            maxConcurrentOperations = max(maxConcurrentOperations, activeOperations)
            operations.append(operation)
        }
    }

    private final class FailureBox {
        var values: [Error?] = []
    }

    private func makeCoordinator(
        _ driver: Driver,
        failures: FailureBox
    ) -> CarPlayNavigationCoordinator<Driver> {
        CarPlayNavigationCoordinator(driver: driver, onFailure: { failures.values.append($0) })
    }

    func testDepthResetRequiresSuccessfulCompletionAndFreshDepthCheck() {
        let driver = Driver()
        driver.templates = (0..<5).map { _ in Template() }
        let failures = FailureBox()
        let coordinator = makeCoordinator(driver, failures: failures)
        coordinator.startSession()
        let destination = Template()

        coordinator.pushTemplate(destination, animated: true)
        XCTAssertEqual(driver.operations.count, 1)
        if case .popToRoot = driver.operations[0] {} else { XCTFail("Expected reset") }

        driver.completeNext(success: true, mutateStack: false)
        XCTAssertEqual(driver.operations.count, 0)
        XCTAssertEqual(failures.values.count, 1)
    }

    func testSuccessfulResetThenPushStaysWithinDepthLimit() {
        let driver = Driver()
        driver.templates = (0..<5).map { _ in Template() }
        let failures = FailureBox()
        let coordinator = makeCoordinator(driver, failures: failures)
        coordinator.startSession()
        let destination = Template()

        coordinator.pushTemplate(destination, animated: true)
        driver.completeNext(success: true)
        XCTAssertEqual(driver.templates.count, 1)
        if case let .push(template, _) = driver.operations[0] {
            XCTAssertTrue(template === destination)
        } else { XCTFail("Expected push after successful reset") }
        driver.completeNext(success: true)
        XCTAssertEqual(driver.templates.count, 2)
        XCTAssertTrue(failures.values.isEmpty)
    }

    func testFailedResetDoesNotPushOrRemainBusy() {
        let driver = Driver()
        driver.templates = (0..<5).map { _ in Template() }
        let failures = FailureBox()
        let coordinator = makeCoordinator(driver, failures: failures)
        coordinator.startSession()

        coordinator.pushTemplate(Template(), animated: false)
        driver.completeNext(success: false)
        XCTAssertEqual(driver.operations.count, 0)
        XCTAssertEqual(driver.templates.count, 5)
        XCTAssertEqual(failures.values.count, 1)

        let recovery = Template()
        coordinator.pushTemplate(recovery, animated: false)
        XCTAssertEqual(driver.operations.count, 1)
        if case .popToRoot = driver.operations[0] {} else { XCTFail("Expected recovery reset") }
    }

    func testDistinctDuplicateTapsAreRejectedWhileMutationIsInFlight() {
        let driver = Driver()
        driver.templates = [Template()]
        let failures = FailureBox()
        let coordinator = makeCoordinator(driver, failures: failures)
        coordinator.startSession()
        let first = Template()
        let second = Template()
        let third = Template()

        coordinator.pushTemplate(first, animated: true)
        coordinator.pushTemplate(second, animated: true)
        coordinator.pushTemplate(third, animated: true)
        XCTAssertEqual(driver.operations.count, 1)
        driver.completeNext(success: true)
        XCTAssertEqual(driver.operations.count, 0)
        XCTAssertEqual(driver.templates.count, 2)
        XCTAssertTrue(driver.templates.last === first)
        XCTAssertEqual(driver.maxConcurrentOperations, 1)
        XCTAssertTrue(failures.values.isEmpty)
    }

    func testAThenBThenAHasNoBacklog() {
        let driver = Driver()
        driver.templates = [Template()]
        let coordinator = makeCoordinator(driver, failures: FailureBox())
        coordinator.startSession()
        let a = Template()
        let b = Template()

        coordinator.pushTemplate(a, animated: true)
        coordinator.pushTemplate(b, animated: true)
        coordinator.pushTemplate(a, animated: true)
        driver.completeNext(success: true)

        XCTAssertEqual(driver.operations.count, 0)
        XCTAssertEqual(driver.templates.count, 2)
        XCTAssertTrue(driver.templates.last === a)
    }

    func testPushesAreIgnoredWhileRootIsInstalling() {
        let driver = Driver()
        let coordinator = makeCoordinator(driver, failures: FailureBox())
        coordinator.startSession()
        let root = Template()
        let destination = Template()

        coordinator.setRootTemplate(root, animated: true)
        coordinator.pushTemplate(destination, animated: true)
        XCTAssertEqual(driver.operations.count, 1)
        if case .setRoot = driver.operations[0] {} else { XCTFail("Expected root installation") }
        driver.completeNext(success: true)
        XCTAssertEqual(driver.templates.count, 1)
        XCTAssertTrue(driver.templates.first === root)
        XCTAssertEqual(driver.operations.count, 0)
    }

    func testSingletonAtTopIsReusedWithoutMutation() {
        let driver = Driver()
        let root = Template()
        let nowPlaying = Template()
        driver.templates = [root, nowPlaying]
        let coordinator = makeCoordinator(driver, failures: FailureBox())
        coordinator.startSession()

        coordinator.showSingletonTemplate(nowPlaying, animated: true)
        XCTAssertTrue(driver.operations.isEmpty)
        XCTAssertTrue(driver.templates.last === nowPlaying)
    }

    func testSingletonExistingInstanceIsPoppedToAndDuplicateTapIsRejected() {
        let driver = Driver()
        let root = Template()
        let nowPlaying = Template()
        driver.templates = [root, nowPlaying, Template(), Template()]
        let coordinator = makeCoordinator(driver, failures: FailureBox())
        coordinator.startSession()

        coordinator.showSingletonTemplate(nowPlaying, animated: true)
        coordinator.showSingletonTemplate(nowPlaying, animated: true)
        XCTAssertEqual(driver.operations.count, 1)
        if case let .pop(template, _) = driver.operations[0] {
            XCTAssertTrue(template === nowPlaying)
        } else { XCTFail("Expected pop to retained singleton") }
        driver.completeNext(success: true)
        XCTAssertEqual(driver.templates.count, 2)
        XCTAssertTrue(driver.templates.last === nowPlaying)
    }

    func testInvalidatedRequestCannotRunItsStaleCompletionAfterRestart() {
        let driver = Driver()
        driver.templates = [Template()]
        let coordinator = makeCoordinator(driver, failures: FailureBox())
        coordinator.startSession()
        coordinator.pushTemplate(Template(), animated: true)
        coordinator.invalidateSession()
        coordinator.startSession()
        let fresh = Template()
        coordinator.pushTemplate(fresh, animated: true)
        XCTAssertEqual(driver.operations.count, 2)

        driver.completeNext(success: true)
        XCTAssertEqual(driver.operations.count, 1)
        XCTAssertFalse(driver.templates.contains(where: { $0 === fresh }))
        driver.completeNext(success: true)
        XCTAssertTrue(driver.templates.last === fresh)
    }
}
