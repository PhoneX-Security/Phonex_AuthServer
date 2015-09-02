package com.phoenix.db.tools;

import org.hibernate.dialect.MySQL5InnoDBDialect;

/**
 * Database dialect to be used with hibernate.
 * InnoDB + UTF8.
 *
 * Created by dusanklinec on 02.09.15.
 */
public class PhonexDBDialect extends MySQL5InnoDBDialect {
    public String getTableTypeString() {
        return " ENGINE=InnoDB DEFAULT CHARSET=utf8";
    }
}
