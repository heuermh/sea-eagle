/*
 * The authors of this file license it to you under the
 * Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You
 * may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.github.heuermh.seaeagle;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import java.nio.file.Files;

import java.nio.file.attribute.PosixFilePermission;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * History file.
 */
final class HistoryFile {
    private final String fileName;
    static final String DEFAULT_FILE_NAME = ".se_history";
    static final Logger logger = LoggerFactory.getLogger(HistoryFile.class);


    HistoryFile() {
        this(DEFAULT_FILE_NAME);
    }

    HistoryFile(final String fileName) {
        //checkNotNull(fileName);
        this.fileName = fileName;
    }

    void append(final String value) {
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(getHistoryFile(fileName), true)))) {
            writer.println(value);
        }
        catch (IOException e) {
            logger.warn("Could not write to history file, caught I/O exception", e);
        }
    }

    static File getHistoryFile(final String fileName) throws IOException {
        File homeDirectory = new File(System.getProperty("user.home"));
        File historyFile = new File(homeDirectory, fileName);

        if (historyFile.exists()) {
            checkFilePermissions(historyFile);
        }
        else {
            createHistoryFile(historyFile);
        }
        logger.info("Opened history file {} for writing", historyFile);
        return historyFile;
    }
    
    static void createHistoryFile(final File historyFile) throws IOException {
        historyFile.createNewFile();
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        Files.setPosixFilePermissions(historyFile.toPath(), perms);
    }

    static void checkFilePermissions(final File historyFile) throws IOException {
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(historyFile.toPath());
        if (!perms.contains(PosixFilePermission.OWNER_READ)) {
            logger.warn("History file {} has incorrect posix file permissions, missing OWNER_READ", historyFile);
        }
        if (!perms.contains(PosixFilePermission.OWNER_WRITE)) {
            logger.warn("History file {} has incorrect posix file permissions, missing OWNER_WRITE", historyFile);
        }
        if (perms.contains(PosixFilePermission.GROUP_READ)) {
            logger.warn("History file {} has incorrect posix file permissions, should not have GROUP_READ", historyFile);
        }
        if (perms.contains(PosixFilePermission.GROUP_WRITE)) {
            logger.warn("History file {} has incorrect posix file permissions, should not have GROUP_WRITE", historyFile);
        }
        if (perms.contains(PosixFilePermission.OTHERS_READ)) {
            logger.warn("History file {} has incorrect posix file permissions, should not have OTHERS_READ", historyFile);
        }
        if (perms.contains(PosixFilePermission.OTHERS_WRITE)) {
            logger.warn("History file {} has incorrect posix file permissions, should not have OTHERS_READ", historyFile);
        }
    }
}
