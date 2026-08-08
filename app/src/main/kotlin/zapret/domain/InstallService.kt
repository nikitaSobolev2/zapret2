package zapret.domain

import kotlin.time.Duration.Companion.minutes

class InstallFailed(message: String) : Exception(message)

/** Seeds user lists and installs/starts the utunws LaunchDaemon from the bundled payload. */
class InstallService(
    private val privileges: PrivilegeRunner,
    private val lists: EngineListsStore = EngineListsStore(),
    private val service: ZapretService = ZapretService(privileges, lists),
) {

    fun install(onStep: (String) -> Unit): CommandResult {
        val payload = ZapretPaths.enginePayload()
            ?: throw InstallFailed("Пакет двигателя не найден. Переустановите приложение из DMG.")
        if (!ZapretPaths.hasPrebuiltUtunws(payload)) {
            throw InstallFailed("Бинарник utunws отсутствует. Пересоберите приложение.")
        }
        if (!ZapretPaths.isValidUserDataRoot(ZapretPaths.userDataRoot)) {
            throw InstallFailed("Некорректный путь данных пользователя")
        }

        onStep("Подготовка списков")
        lists.ensureSeeded()

        onStep("Установка utunws")
        return EnginePrivileged.install(
            privileges = privileges,
            payload = payload,
            dataRoot = ZapretPaths.userDataRoot,
            timeout = 5.minutes,
        ).also { service.status() }
    }
}
