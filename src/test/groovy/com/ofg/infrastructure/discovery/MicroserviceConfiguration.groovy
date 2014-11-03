package com.ofg.infrastructure.discovery

class MicroserviceConfiguration {

    public static final String VALID_CONFIGURATION = """
                            {
                                "pl": {
                                    "this": "com/ofg/service",
                                    "dependencies": {
                                        "ping": "com/ofg/ping",
                                        "pong": "com/ofg/pong"
                                    }
                                }
                            }
                            """

    public static final String MISSING_THIS_ELEMENT = """
                            {
                                "pl": {
                                    "dependencies": {
                                        "ping": "com/ofg/ping"
                                    }
                                }
                            }
                            """

    public static final String INVALID_DEPENDENCIES_ELEMENT = """
                            {
                                "pl": {
                                    "this": "com/ofg/service",
                                    "dependencies": "no"
                                }
                            }
                            """

    public static final String MULTIPLE_ROOT_ELEMENTS = """
                            {
                                "pl": {
                                    "this": "com/ofg/service",
                                },
                                "lv": {
                                    "this": "com/ofg/ping",
                                }
                            }
                            """

    public static final String ONLY_REQUIRED_ELEMENTS = """
                            {
                                "pl": {
                                    "this": "com/ofg/service"
                                    }
                                }
                            }
                            """
}