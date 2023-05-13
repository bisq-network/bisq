package bisq.gradle.tasks

object PgpFingerprint {
    fun normalize(fingerprint: String): String =
        fingerprint.filterNot { it.isWhitespace() }  // Remove all spaces
            .toLowerCase()
}