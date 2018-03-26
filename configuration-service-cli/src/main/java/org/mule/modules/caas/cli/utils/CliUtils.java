package org.mule.modules.caas.cli.utils;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;

import java.io.Console;
import java.util.Arrays;

public class CliUtils {

    public static char[] readPassword() {
        Console c = System.console();
        return c.readPassword();
    }

    public static char[] readPassword(String prompt, Logger logger, int minLenght) {
        char[] password = null;
        char[] ver = null;
        do {
            logger.info(prompt);
            password = readPassword();

            if (ArrayUtils.getLength(password) < minLenght) {
                logger.info("Password should contain at least {} characters", minLenght);
            }

            logger.info("Please re-type for verification: ");
            ver = readPassword();

        } while (ArrayUtils.getLength(password) > minLenght && !Arrays.equals(password, ver));

        return password;
    }

}
