
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. The ASF licenses this file to You under
 * the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License.  You may obtain a copy of
 * the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.reactome.server.tools.indexer.util;

/*
 * From: https://github.com/ansgarwiechers/solrpasswordhash
 *
 * Adopted from the Solr Sha256AuthenticationProvider class.
 * (solr/core/src/java/org/apache/solr/security/Sha256AuthenticationProvider.java)
 *
 * URL: <https://github.com/apache/lucene-solr>
 */

import org.apache.commons.codec.binary.Base64;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;

public class SHA256SolrPassword {

    private static String FILENAME = "security.json";
    private static String SECURITY_JSON= "{\"authentication\": {\"blockUnknown\": true, \"class\": \"solr.BasicAuthPlugin\", \"credentials\": {\"admin\": \"##PASSWORD##\"}},\"authorization\": {\"class\": \"solr.RuleBasedAuthorizationPlugin\",\"permissions\": [{\"name\": \"security-edit\",\"role\": \"admin\"}],\"user-role\": {\"solr\": \"admin\"}}}";

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Please include the password as a parameter. Run again.");
            System.exit(1);
        }

        String pw = args[0];
        String solrDataDir = args[1];
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");

            final Random r = new SecureRandom();
            byte[] salt = new byte[32];
            r.nextBytes(salt);

            digest.reset();
            digest.update(salt);
            byte[] btPass = digest.digest(pw.getBytes(StandardCharsets.UTF_8));
            digest.reset();
            btPass = digest.digest(btPass);

            String passwordAndSalt = Base64.encodeBase64String(btPass) + " " + Base64.encodeBase64String(salt);
            String securityJsonFile = SECURITY_JSON.replace("##PASSWORD##", passwordAndSalt);

            Files.write(Paths.get(solrDataDir, FILENAME), securityJsonFile.getBytes());

            System.exit(0);

        } catch (NoSuchAlgorithmException e) {
            System.err.println("Unknown algorithm: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Couldn't create security.json file: " + e.getMessage());
        }

        System.exit(2);
    }
}