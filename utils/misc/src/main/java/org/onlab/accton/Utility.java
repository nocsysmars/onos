/*
 * Copyright(c) 2025-present Accton. All rights reserved. www.accton.com
 */

/**
 * Utilities for accton.
 */
package org.onlab.accton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;


/**
 * Definitions of lib.
 */
public final class Utility {

    public static String getElasticSearchHostsIp() {
        return Optional.ofNullable(System.getenv("ES_HOSTS"))
                .orElse("127.0.0.1:9200");
    }

    public static String getEsUrl() {
        return "http://" + getElasticSearchHostsIp() + "/";
    }

    public static String getESDoc() {
        return Optional.ofNullable(System.getenv("ES_DOC"))
                       .orElse("_doc");
    }

    public static String getCurrentUtcTimeString() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return simpleDateFormat.format(new Date());
    }

}