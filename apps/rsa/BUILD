COMPILE_DEPS = CORE_DEPS + JACKSON + REST + [
    "//apps/optical-model:onos-apps-optical-model",
]

APPS = [
    "org.onosproject.optical-model",
]

osgi_jar_with_tests(
    api_description = "REST API for RSA (Routing and Spectrum Assignment) decisions",
    api_package = "org.onosproject.rsa.web",
    api_title = "RSA Optical REST API",
    api_version = "1.0",
    web_context = "/onos/rsa",
    deps = COMPILE_DEPS,
)

onos_app(
    app_name = "org.onosproject.rsa",
    category = "Optical",
    description = "RSA (Routing and Spectrum Assignment) decision application. " +
                  "Computes k-shortest-path routing and First-Fit spectrum assignment " +
                  "(enforcing contiguity, continuity and non-overlap constraints) over a " +
                  "spectrum occupancy table, then submits the decision as an ONOS intent.",
    required_apps = APPS,
    title = "RSA Optical REST API",
    url = "http://onosproject.org",
)
