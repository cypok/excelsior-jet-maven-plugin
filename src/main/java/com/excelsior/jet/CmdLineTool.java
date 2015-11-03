/*
 * Copyright (c) 2015, Excelsior LLC.
 *
 *  This file is part of Excelsior JET Maven Plugin.
 *
 *  Excelsior JET Maven Plugin is free software:
 *  you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Excelsior JET Maven Plugin is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Excelsior JET Maven Plugin.
 *  If not, see <http://www.gnu.org/licenses/>.
 *
*/
package com.excelsior.jet;

import org.apache.maven.plugin.logging.Log;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * A wrapper around {@link ProcessBuilder} that redirects output to Maven {@link Log}.
 *
 * @author Nikita Lipsky
 */
public class CmdLineTool {

    private String[] args;
    private Log log;
    private File workDir;
    private HashMap<String, String> env = new HashMap<>();

    public CmdLineTool(String... args) {
        this.args = args;
    }

    public CmdLineTool withLog(Log log) {
        this.log = log;
        return this;
    }

    public CmdLineTool workingDirectory(File workDir) {
        this.workDir = workDir;
        return this;
    }

    public CmdLineTool withEnvironment(String env) {
        int pos = env.indexOf('=');
        if (pos < 0) throw new IllegalArgumentException();
        String key = env.substring(0, pos);
        this.env.put(Utils.isWindows() ? key.toUpperCase() : key, env.substring(pos+1));
        return this;
    }

    private class OutputReader extends Thread{

        BufferedReader reader;
        boolean err;

        OutputReader(InputStream stream, boolean err){
            this.reader = new BufferedReader(new InputStreamReader(stream));
            this.err = err;
        }

        public void run() {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (log != null) {
                        if (err) {
                            log.error(line);
                        } else {
                            log.info(line);
                        }
                    }
                }
            } catch (IOException e) {
            }
        }
    }

    public int execute() throws CmdLineToolException {
        try {
            ProcessBuilder pb = new ProcessBuilder(args).directory(workDir);
            if (!env.isEmpty()) {
                Map<String, String> penv = pb.environment();
                if (Utils.isWindows()) {
                    for (String key : penv.keySet()) {
                        String keyUpper = key.toUpperCase();
                        if (env.containsKey(keyUpper)) {
                            penv.put(key, env.get(keyUpper));
                            env.remove(keyUpper);
                        }
                    }
                }
                penv.putAll(env);
            }
            Process process = pb.start();
            OutputReader inreader = new OutputReader(process.getInputStream(),false);
            inreader.start();
            OutputReader errreader = new OutputReader(process.getErrorStream(), true);
            errreader.start();
            int exitCode = process.waitFor();
            inreader.join(0);
            errreader.join(0);
            return exitCode;
        } catch (IOException | InterruptedException e) {
            throw new CmdLineToolException(e);
        }

    }
}