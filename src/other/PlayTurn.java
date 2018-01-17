package other;

import bot.DuelPlanetWars;
import bot.MyBot;
import java.io.PrintWriter;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author Sander
 */
public class PlayTurn extends MyBot {

    public static void main(String[] args) {
        String gameState =
                "P 11.085315 11.575710 0 85 0\n"
                + "P 16.116537 2.288005 1 5 5\n"
                + "P 6.054093 20.863415 2 5 5\n"
                + "P 8.564119 18.813229 2 80 2\n"
                + "P 13.606511 4.338191 1 27 2\n"
                + "P 1.872344 18.548748 2 5 5\n"
                + "P 20.298285 4.602672 1 5 5\n"
                + "P 6.513372 7.938571 2 2 2\n"
                + "P 15.657258 15.212849 1 13 2\n"
                + "P 17.312679 11.977029 1 4 3\n"
                + "P 4.857951 11.174391 2 22 3\n"
                + "P 13.777017 23.151420 2 11 2\n"
                + "P 8.393613 0.000000 1 2 2\n"
                + "P 19.690335 8.397629 1 3 3\n"
                + "P 2.480295 14.753791 2 3 3\n"
                + "P 20.199453 6.639009 1 4 4\n"
                + "P 1.971177 16.512411 2 4 4\n"
                + "P 7.426311 17.386134 2 4 4\n"
                + "P 14.744319 5.765286 1 16 4\n"
                + "P 22.170630 14.676853 1 1 1\n"
                + "P 0.000000 8.474567 2 1 1\n"
                + "P 12.950082 11.429141 1 5 5\n"
                + "P 9.220548 11.722279 1 12 5\n"
                + "F 2 3 10 12 12 5\n"
                + "F 2 1 20 12 12 5\n"
                + "F 2 4 22 12 12 5\n"
                + "F 2 3 14 3 8 1\n"
                + "F 1 18 4 7 8 2\n"
                + "F 2 6 22 12 12 6\n"
                + "F 2 1 10 12 12 6\n"
                + "F 2 19 3 8 8 2\n"
                + "F 2 5 5 10 8 2\n"
                + "F 2 4 16 10 7 1\n"
                + "F 2 5 5 22 11 6\n"
                + "F 2 4 14 22 8 3\n"
                + "F 2 4 16 22 9 4\n"
                + "F 2 1 20 22 10 5\n"
                + "F 1 5 6 18 6 1\n"
                + "F 1 4 15 18 6 1\n"
                + "F 1 3 13 18 6 1\n"
                + "F 1 1 19 8 7 2\n"
                + "F 2 7 10 22 5 1\n"
                + "F 2 5 5 22 11 7\n"
                + "F 2 4 14 22 8 4\n"
                + "F 2 4 16 22 9 5\n"
                + "F 2 1 20 22 10 6\n"
                + "F 2 4 10 22 5 1\n"
                + "F 1 5 6 18 6 2\n"
                + "F 1 4 15 18 6 2\n"
                + "F 1 3 13 18 6 2\n"
                + "F 1 1 19 8 7 3\n"
                + "F 2 1 17 22 6 3\n"
                + "F 2 5 5 22 11 8\n"
                + "F 2 3 14 22 8 5\n"
                + "F 2 4 16 22 9 6\n"
                + "F 2 1 20 22 10 7\n"
                + "F 2 5 2 3 4 1\n"
                + "F 1 5 1 4 4 1\n"
                + "F 1 5 6 18 6 3\n"
                + "F 1 4 15 18 6 3\n"
                + "F 1 3 13 18 6 3\n"
                + "F 1 1 19 8 7 4\n"
                + "F 1 4 9 8 4 1\n"
                + "F 1 35 21 22 4 1\n"
                + "F 2 5 5 3 7 5\n"
                + "F 2 3 14 3 8 6\n"
                + "F 2 4 16 3 7 5\n"
                + "F 2 1 20 7 7 5\n"
                + "F 2 31 10 7 4 2\n"
                + "F 2 5 2 3 4 2\n"
                + "F 1 5 1 4 4 2\n"
                + "F 1 5 6 18 6 4\n"
                + "F 1 4 15 18 6 4\n"
                + "F 1 3 13 18 6 4\n"
                + "F 1 1 19 8 7 5\n"
                + "F 1 4 9 8 4 2\n"
                + "F 1 136 21 22 4 2\n"
                + "F 1 5 1 4 4 3\n"
                + "F 1 20 18 4 2 1\n"
                + "F 1 5 6 18 6 5\n"
                + "F 1 4 15 18 6 5\n"
                + "F 1 3 13 18 6 5\n"
                + "F 1 1 19 8 7 6\n"
                + "F 1 4 9 8 4 3\n"
                + "F 1 18 21 22 4 3\n"
                + "F 1 90 4 7 8 7\n"
                + "F 1 21 8 3 8 7\n"
                + "F 1 19 22 7 5 4\n"
                + "F 2 94 3 8 8 7\n"
                + "F 2 5 5 10 8 7\n"
                + "F 2 3 14 10 5 4\n"
                + "F 2 4 16 10 7 6\n"
                + "F 2 1 20 10 6 5\n"
                + "F 2 5 2 3 4 3\n"
                + "F 2 4 17 3 2 1\n"
                + "F 2 14 7 10 4 3";

        turn = 197;
        MyBot.log = new PrintWriter(System.out);
        DuelPlanetWars pw = new DuelPlanetWars(gameState);
        pw.setLogger(MyBot.log);
        doTurn(pw);
    }
}