/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package other;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 *
 * @author Sander
 */
public class IdleBot {

    public static void main(String[] args) throws IOException {
        String line;

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

        while ((line = in.readLine()) != null) {
            if (line.equals("go")) {
                System.out.println("go");
                System.out.flush();
            }
        }
    }
}
