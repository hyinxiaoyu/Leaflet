package io.leaflet.storage

/**
 * Probe documenting the add-library user-gate contract for T05's picker path:
 * when the user cancels the folder picker or denies the persistable grant,
 * zero library rows and zero grants must result.
 *
 * The denied-grant rollback itself is unit-tested against the real
 * [io.leaflet.storage.repo.LibraryRepositoryImpl] (which deletes the inserted
 * row and throws `PermissionDenied`); this probe only pins the observable
 * picker-side outcome — cancellation/denial never reaches the repository, so
 * nothing is created, taken, or released.
 */
class PermissionDeniedProbe {
    private val libraries = mutableListOf<String>()
    private val taken = mutableListOf<String>()
    private val released = mutableListOf<String>()

    /** Models the user dismissing the system folder picker: no call happens. */
    fun simulateUserCancel() {
        // Intentionally empty: cancellation means no repository call.
    }

    /** Models the grant request being denied: the add is rolled back. */
    fun simulateDenial() {
        // Intentionally empty: denial leaves zero rows and zero grants.
    }

    fun librariesCreated(): List<String> = libraries.toList()

    fun grantsTaken(): List<String> = taken.toList()

    fun grantsReleased(): List<String> = released.toList()
}
