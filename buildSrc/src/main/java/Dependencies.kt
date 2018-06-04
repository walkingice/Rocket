object Versions {
    const val min_sdk = 21
    const val target_sdk = 27
    const val compile_sdk = 27
    const val build_tools = "27.0.3"
    const val version_code = 1
    const val version_name = "2.1.1"
    const val android_gradle_plugin = "3.2.0-alpha16"
    const val dicedmelon_jacoco_plugin = "0.1.3"
    const val gms_oss_licenses_plugin = "0.9.1"
    const val support = "27.1.1"
    const val room = "1.0.0"
    const val glide = "4.0.0"
    const val kotlin = "1.2.40"
    const val gms = "11.8.0"
    const val findbugs = "3.0.1"
    const val lottie = "2.2.0"
    const val leakcanary = "1.5.4"
    const val telemetry = "1.1.1"
    const val adjust = "4.11.4"
    const val junit = "4.12"
    const val mockito = "2.12.0"
    const val robolectric = "3.5.1"
    const val espresso = "3.0.1"
    const val test_runner = "1.0.1"
    const val uiautomator = "2.1.3"
    const val mockwebserver = "3.7.0"
    const val firebase = "16.0.0"
    const val fcm = "17.0.0"
    const val crashlytics = "2.9.3"
    const val google_services_plugin = "3.1.1"
    const val fabric_plugin = "1.25.1"

}

object SystemEnv {
    val google_app_id: String? = System.getenv("google_app_id")
    val default_web_client_id: String? = System.getenv("default_web_client_id")
    val firebase_database_url: String? = System.getenv("firebase_database_url")
    val gcm_defaultSenderId: String? = System.getenv("gcm_defaultSenderId")
    val google_api_key: String? = System.getenv("google_api_key")
    val google_crash_reporting_api_key: String? = System.getenv("google_crash_reporting_api_key")
    val project_id: String? = System.getenv("project_id")
}
