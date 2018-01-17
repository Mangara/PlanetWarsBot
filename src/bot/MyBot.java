package bot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

public class MyBot {

    //// DEBUG FLAGS ////
    public static final boolean DEBUG_ATTACK = false;
    public static final boolean DEBUG_DEFENSE = false;
    public static final boolean DEBUG_PREDICTIONS = false;
    public static final boolean DEBUG_SUPPLY = false;
    public static final boolean DEBUG = DEBUG_ATTACK || DEBUG_DEFENSE || DEBUG_PREDICTIONS || DEBUG_SUPPLY;
    //// DEBUG FLAGS ////
    public static PrintWriter log;
    protected static int turn = 0;
    private static final int MAX_TURNS = 201;
    private static boolean defensive = true;
    private static int cooldown = 50;
    private static FleetPatternAnalyzer fleetAnalyzer = new FleetPatternAnalyzer();

    public static void doTurn(DuelPlanetWars pw) {
        turn++;

        if (DEBUG) {
            log.println("Started turn " + turn + ". Turns left: " + (MAX_TURNS - turn));
        }

        // Predict the future states of all planets
        Prediction2[] predictions = predictFuture(pw);

        // switch between defensive and aggressive modes
        selectState(pw, predictions);

        if (defensive) {
            // Defend planets that are under attack
            defend(pw, predictions);

            // Attack vulnerable planets
            attack(pw, predictions);

            // Send reinforcements to the front lines
            supply(pw, predictions);
        } else {
            // Defend planets that are under attack
            defend(pw, predictions);

            // Send reinforcements to the front lines
            supply(pw, predictions);

            // Attack the closest enemy planets
            rage(pw, predictions);
        }

        if (DEBUG) {
            log.flush();
        }

        return;
    }

    public static void main(String[] args) throws IOException {
        if (DEBUG) {
            log = new PrintWriter("Log2.txt");
            //log = new PrintWriter("Log" + (new Random()).nextInt(500) + ".txt");
            //log = new PrintWriter(System.err);
        }

        String line;
        String message = "";

        DuelPlanetWars pw = null;
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

        while ((line = in.readLine()) != null) {
            if (line.equals("go")) {
                if (pw == null) {
                    pw = new DuelPlanetWars(message);

                    //// DEBUG ////
                    //pw.setLogger(log);
                    //// DEBUG ////
                } else {
                    pw.finalizeUpdate();
                }

                doTurn(pw);
                pw.finishTurn();
                pw.initializeUpdate();

                message = "";
            } else {
                if (pw == null) {
                    message += line + "\n";
                } else {
                    pw.updateLine(line);
                }
            }
        }
    }

    private static void selectState(DuelPlanetWars pw, Prediction2[] predictions) {
        if (cooldown > 0) {
            // Play defensive in the early game; don't overcommit
            // Play defensively after raging
            cooldown--;
            defensive = true;
        } else {
            // If this is a standstill that I'm losing, go aggressive
            for (Fleet fleet : pw.getFleets()) {
                if (pw.getPlanet(fleet.getDestination()).getOwner() != fleet.getOwner()) {
                    // Someone is attacking stuff, this is not a standstill
                    defensive = true;
                    return;
                }
            }

            int myShipsFinal = 0;
            int enemyShipsFinal = 0;

            for (Prediction2 pred : predictions) {
                int owner = pred.getOwnerAt(MAX_TURNS - turn);

                if (owner == 1) {
                    myShipsFinal += pred.getNumShipsAt(MAX_TURNS - turn);
                } else if (owner == 2) {
                    enemyShipsFinal += pred.getNumShipsAt(MAX_TURNS - turn);
                }
            }

            if (enemyShipsFinal > myShipsFinal) {
                defensive = false;
                cooldown = 10; // Play defensively for 10 turns after this
            } else {
                defensive = true;
            }
        }
    }

    private static void defend(final DuelPlanetWars pw, Prediction2[] predictions) {
        // Find out which planets are under attack, sorted by importance (highest -> lowest)
        List<Planet> attacked = getPlanetsUnderAttack(pw, predictions);

        if (DEBUG_DEFENSE) {
            log.println("Planets under attack:");
            for (Planet p : attacked) {
                log.println(p);
            }
        }

        // Figure out how best to defend each planet, if at all possible
        for (Planet planet : attacked) {
            if (DEBUG_DEFENSE) {
                log.println("Defending " + planet);
                log.flush();
            }

            // Determine the best defense strategy
            Prediction2 pred = predictions[planet.getPlanetID()];
            boolean defendable = true;

            while (pred.getOwnerAt(MAX_TURNS - turn) != 1 && defendable) {
                Pair<Integer, Integer> takeOver = pred.getLastHostileTakeOver();

                if (takeOver == null) {
                    // Can occur if we are predicted to own this planet again after the end of the game
                    defendable = false;
                    break;
                }

                int takeOverTurn = takeOver.getFirst();
                int shipsNeeded = takeOver.getSecond();
                int totalShipsAvailable = 0;

                if (DEBUG_DEFENSE) {
                    log.println("takeOverTurn: " + takeOverTurn + " shipsNeeded: " + shipsNeeded);
                }

                List<Pair<Planet, Pair<Integer, Double>>> defenders = new ArrayList<Pair<Planet, Pair<Integer, Double>>>();

                // Can we defend in time?
                for (Planet p : pw.getClosestPlanets(planet)) {
                    int distance = pw.getDistance(p, planet);

                    if (distance <= takeOverTurn) {
                        // Ships from this planet can make it in time
                        int shipsAvailable = predictions[p.getPlanetID()].getSurplusAt(takeOverTurn - distance);

                        if (shipsAvailable > 0) {
                            totalShipsAvailable += shipsAvailable;
                            defenders.add(new Pair<Planet, Pair<Integer, Double>>(p, new Pair<Integer, Double>(shipsAvailable, 0.0)));
                        }
                    } else {
                        // The planets are sorted, others won't make it in time either
                        break;
                    }
                }

                if (DEBUG_DEFENSE) {
                    log.println("Available defenders:");
                    for (Pair<Planet, Pair<Integer, Double>> pair : defenders) {
                        log.println(pair.getFirst() + " with " + pair.getSecond().getFirst() + " ships.");
                    }
                }

                if (totalShipsAvailable >= shipsNeeded) {
                    // We can defend in time: decide on the distribution
                    for (Pair<Planet, Pair<Integer, Double>> defender : defenders) {
                        defender.getSecond().setSecond(computeFleetCost(pw, predictions, defender.getFirst(), defender.getSecond().getFirst(), takeOverTurn, pw.getDistance(defender.getFirst(), planet), shipsNeeded, totalShipsAvailable));
                    }

                    // Sort the defenders by cost
                    Collections.sort(defenders, new Comparator<Pair<Planet, Pair<Integer, Double>>>() {

                        public int compare(Pair<Planet, Pair<Integer, Double>> o1, Pair<Planet, Pair<Integer, Double>> o2) {
                            return Double.compare(o1.getSecond().getSecond(), o2.getSecond().getSecond());
                        }
                    });

                    if (DEBUG_DEFENSE) {
                        log.println("Sorted defenders:");
                        for (Pair<Planet, Pair<Integer, Double>> pair : defenders) {
                            log.println(pair.getFirst() + " with " + pair.getSecond().getFirst() + " ships, at cost " + pair.getSecond().getSecond());
                        }
                    }

                    // Take as many defenders with the lowest cost as possible, until the number of ships is large enough
                    for (Pair<Planet, Pair<Integer, Double>> defender : defenders) {
                        int shipsToSend = Math.min(shipsNeeded, defender.getSecond().getFirst());
                        int turnsRemaining = takeOverTurn - pw.getDistance(defender.getFirst(), planet);

                        if (DEBUG_DEFENSE) {
                            log.print("Sending " + shipsToSend + " ships from " + defender.getFirst() + " in " + turnsRemaining + " turns.");
                        }

                        // Only send the order now if absolutely necessary
                        if (turnsRemaining == 0 && shipsToSend > 0) {
                            if (DEBUG_DEFENSE) {
                                log.println(" Command issued.");
                            }

                            pw.issueOrder(defender.getFirst(), planet, shipsToSend);
                        } else {
                            if (DEBUG_DEFENSE) {
                                log.println();
                            }
                        }

                        shipsNeeded -= shipsToSend;

                        // Make sure we don't try to send more troops from this planet than possible and record the incoming fleet at the target
                        predictions[defender.getFirst().getPlanetID()].addOutgoingFleet(1, shipsToSend, turnsRemaining, true);
                        predictions[planet.getPlanetID()].addIncomingFleet(1, shipsToSend, takeOverTurn, false);

                        if (DEBUG_DEFENSE) {
                            log.print("New prediction for planet " + defender.getFirst() + ": ");

                            Prediction2 p = predictions[defender.getFirst().getPlanetID()];
                            int n = 20;

                            for (int i = 0; i < n - 1; i++) {
                                log.print(p.getNumShipsAt(i, true) + ", ");
                            }

                            log.println(p.getNumShipsAt(n - 1, true));

                            log.println("Planet's surplus: " + p.getSurplusAt(0));
                        }

                        if (shipsNeeded == 0) {
                            break;
                        }
                    }

                    // Update predictions for the target
                    predictions[planet.getPlanetID()].computePredictions();
                } else {
                    // We can't defend in time =(
                    if (DEBUG_DEFENSE) {
                        log.println("That isn't enough, let this one fall.");
                    }

                    defendable = false;
                }
            }
        }

        if (DEBUG_DEFENSE) {
            log.flush();
        }
    }

    private static List<Planet> getPlanetsUnderAttack(final DuelPlanetWars pw, Prediction2[] predictions) {
        List<Planet> attacked = new ArrayList<Planet>();

        for (Planet planet : pw.getPlanets()) {
            Prediction2 pred = predictions[planet.getPlanetID()];

            if (pred.isMineSometime() && pred.getOwnerAt(MAX_TURNS - turn) == 2) {
                attacked.add(planet);
            }
        }

        // Sort the attacked planets lexicographically by growth rate and average distance to my planets
        Collections.sort(attacked, new Comparator<Planet>() {

            public int compare(Planet p1, Planet p2) {
                if (p1.getGrowthRate() == p2.getGrowthRate()) {
                    double sumDistance1 = 0;
                    double sumDistance2 = 0;

                    for (Planet planet : pw.getMyPlanets()) {
                        sumDistance1 += pw.getDistance(planet, p1);
                        sumDistance2 += pw.getDistance(planet, p2);
                    }

                    return Double.compare(sumDistance1, sumDistance2);
                } else {
                    return p2.getGrowthRate() - p1.getGrowthRate();
                }
            }
        });

        return attacked;
    }

    private static double computeFleetCost(DuelPlanetWars pw, Prediction2[] predictions, Planet sender, int surplus, int takeOverTurn, int distance, int shipsNeeded, int totalShipsAvailable) {
        // What is the risk this planet will be conquered by the enemy if he attacks the turn after we defend and we send all our available ships and the other planets each send (shipsNeeded - surplus) / (totalShipsAvailable - surplus) fraction of their ships
        // Express the risk as enemyForces nearby / friendlyForces nearby. 'nearby' meaning until the first 3 enemy planets
        int attackTurn = takeOverTurn - distance + 1;
        int shipsToSend = Math.min(shipsNeeded, surplus);

        if (shipsToSend == totalShipsAvailable) {
            // To avoid dividing by zero; we're the only planet anyway
            return 1;
        }

        double surplusLeft = 1 - (shipsNeeded - shipsToSend) / (double) (totalShipsAvailable - shipsToSend);

        int enemyPlanets = 0;
        int enemyTroops = 0;
        double friendlyTroops = 0.000001; // don't divide by zero

        for (Planet p : pw.getClosestPlanets(sender)) {
            int owner = predictions[p.getPlanetID()].getOwnerAt(attackTurn);

            if (owner == 1) {
                friendlyTroops += surplusLeft * predictions[p.getPlanetID()].getSurplusAt(attackTurn);
            } else if (owner == 2) {
                enemyTroops += predictions[p.getPlanetID()].getNumShipsAt(attackTurn);
                enemyPlanets++;

                if (enemyPlanets == 3) {
                    break;
                }
            }
        }

        double risk = enemyTroops / friendlyTroops;

        return distance * risk;
    }

    private static Prediction2[] predictFuture(DuelPlanetWars pw) {
        fleetAnalyzer.newTurn();
        fleetAnalyzer.addFleets(pw.getEnemyFleets());

        if (DEBUG) {
            log.println("Detected streams:");

            for (Fleet fleet : fleetAnalyzer.getStreams()) {
                log.println(fleet);
            }
        }

        // TODO: use the computed streams

        Prediction2[] predictions = new Prediction2[pw.getNumPlanets()];

        for (Planet planet : pw.getPlanets()) {
            predictions[planet.getPlanetID()] = new Prediction2(planet);
        }

        for (Fleet fleet : pw.getMyFleets()) {
            predictions[fleet.getDestination()].addIncomingFleet(fleet, false);
        }

        for (Fleet fleet : pw.getEnemyFleets()) {
            predictions[fleet.getDestination()].addIncomingFleet(fleet, false);
        }

        for (int i = 0; i < predictions.length; i++) {
            predictions[i].computePredictions();
        }

        if (DEBUG) {
            for (Planet planet : pw.getPlanets()) {
                log.print("Prediction for planet " + planet + ": ");

                Prediction2 p = predictions[planet.getPlanetID()];
                int n = 20;

                for (int i = 0; i < n - 1; i++) {
                    log.print(p.getNumShipsAt(i, true) + ", ");
                }

                log.println(p.getNumShipsAt(n - 1, true));
            }
        }

        return predictions;
    }

    private static class Attack implements Comparable<Attack> {

        Planet target;
        List<Planet> attackers;
        int takeOverTurn;
        int closerEnemies;
        int shipsNeeded;
        int shipsAvailable;
        int antiSnipeShipsNeeded;
        double score;

        Attack(Planet target, List<Planet> attackers, int takeOverTurn, int closerEnemies, int shipsNeeded, int shipsAvailable, int antiSnipeShipsNeeded) {
            this.target = target;
            this.attackers = attackers;
            this.takeOverTurn = takeOverTurn;
            this.closerEnemies = closerEnemies;
            this.shipsNeeded = shipsNeeded;
            this.shipsAvailable = shipsAvailable;
            this.antiSnipeShipsNeeded = antiSnipeShipsNeeded;
        }

        public void computeScore(Prediction2[] predictions) {
            int lookAheadTurns = Math.min(MAX_TURNS - turn, 30); // Never estimate ship counts more than 30 turns ahead

            // Let's see how many ships this gives me
            int nResultingShips = 0;

            if (predictions[target.getPlanetID()].getOwnerAt(lookAheadTurns) == 2) {
                nResultingShips = predictions[target.getPlanetID()].getNumShipsAt(lookAheadTurns); // The number of enemy ships we prevent
            }

            if (DEBUG_ATTACK) {
                log.print("Ships needed: " + shipsNeeded);
                log.print(", Ships available: " + shipsAvailable);
                log.print(", Closer enemies: " + closerEnemies);
                log.print(", Anti-snipe ships needed: " + antiSnipeShipsNeeded);
                log.print(", Enemy ships prevented: " + nResultingShips);
                log.print(", Nearby growth: " + target.getNearbyGrowth());
            }

            predictions[target.getPlanetID()].addIncomingFleet(1, shipsAvailable, takeOverTurn, true);
            nResultingShips += predictions[target.getPlanetID()].getNumShipsAt(lookAheadTurns, true);
            predictions[target.getPlanetID()].removeIncomingFleet(1, shipsAvailable, takeOverTurn, true);

            if (DEBUG_ATTACK) {
                log.print(", Total ship gain: " + nResultingShips);
            }

            if (shipsAvailable < shipsNeeded + antiSnipeShipsNeeded) {
                // Don't get sniped
                score = 0;
            } else {
                score = (nResultingShips - shipsAvailable) * target.getNearbyGrowth() / ((double) (shipsNeeded + antiSnipeShipsNeeded) * takeOverTurn);
            }

            if (DEBUG_ATTACK) {
                log.println(", Final score: " + score);
            }
        }

        public int compareTo(Attack o) {
            return -Double.compare(score, o.score);
        }

        @Override
        public String toString() {
            return "Attack{" + "target=" + target + "attackers=" + attackers + "takeOverTurn=" + takeOverTurn + "closerEnemies=" + closerEnemies + "shipsNeeded=" + shipsNeeded + "shipsAvailable=" + shipsAvailable + "score=" + score + '}';
        }
    }

    private static void attack(DuelPlanetWars pw, final Prediction2[] predictions) {
        // Find out which attacks are possible
        if (DEBUG_ATTACK) {
            log.println("Deciding which planets to attack.");
            log.flush();
        }

        List<Attack> attacks = new ArrayList<Attack>();

        for (Planet target : pw.getPlanets()) {
            if (predictions[target.getPlanetID()].getOwnerAt(MAX_TURNS - turn) != 1) {
                Attack attack = findBestAttack(pw, predictions, target);

                if (attack != null) {
                    attacks.add(attack);
                }
            }
        }

        Collections.sort(attacks);

        if (DEBUG_ATTACK) {
            log.println("Sorted attacks:");
            for (Attack attack : attacks) {
                log.println(attack);
            }
            log.flush();
        }

        for (Attack attack : attacks) {
            if (attack.score > 0) {
                if (DEBUG_ATTACK) {
                    log.println("Considering attack " + attack);
                }

                // Is it still possible?
                int shipsAvailable = 0;
                List<Pair<Planet, Pair<Integer, Double>>> attackers = new ArrayList<Pair<Planet, Pair<Integer, Double>>>();

                for (Planet attacker : attack.attackers) {
                    int surplus = getAttackSurplus(pw, predictions, attacker, attack.target, attack.takeOverTurn - pw.getDistance(attacker, attack.target));

                    if (surplus > 0) {
                        shipsAvailable += surplus;
                        attackers.add(new Pair<Planet, Pair<Integer, Double>>(attacker, new Pair<Integer, Double>(surplus, computeFleetCost(pw, predictions, attacker, surplus, attack.takeOverTurn, pw.getDistance(attacker, attack.target), attack.shipsNeeded, attack.shipsAvailable))));
                    }
                }

                if (shipsAvailable >= attack.shipsNeeded) {
                    // Sort the attackers by cost
                    Collections.sort(attackers, new Comparator<Pair<Planet, Pair<Integer, Double>>>() {

                        public int compare(Pair<Planet, Pair<Integer, Double>> o1, Pair<Planet, Pair<Integer, Double>> o2) {
                            return Double.compare(o1.getSecond().getSecond(), o2.getSecond().getSecond());
                        }
                    });

                    int shipsNeeded = attack.shipsNeeded;

                    if (shipsAvailable >= attack.shipsNeeded + attack.antiSnipeShipsNeeded) {
                        // Make sure this attack can't be defended or sniped
                        shipsNeeded += attack.antiSnipeShipsNeeded;
                    }

                    // Take as many attackers with the lowest cost as possible, until the number of ships is large enough
                    for (Pair<Planet, Pair<Integer, Double>> attacker : attackers) {
                        int shipsToSend = Math.min(shipsNeeded, attacker.getSecond().getFirst());
                        int turnsRemaining = attack.takeOverTurn - pw.getDistance(attacker.getFirst(), attack.target);

                        if (DEBUG_ATTACK) {
                            log.print("Sending " + shipsToSend + " ships from " + attacker.getFirst() + " in " + turnsRemaining + " turns.");
                        }

                        // Only send the order now if absolutely necessary
                        if (turnsRemaining == 0 && shipsToSend > 0) {
                            if (DEBUG_ATTACK) {
                                log.println(" Command issued.");
                            }
                            pw.issueOrder(attacker.getFirst(), attack.target, shipsToSend);
                        } else {
                            if (DEBUG_ATTACK) {
                                log.println();
                            }
                        }

                        shipsNeeded -= shipsToSend;

                        // Make sure we don't try to send more troops from this planet than possible and record the incoming fleet at the target
                        predictions[attacker.getFirst().getPlanetID()].addOutgoingFleet(1, shipsToSend, turnsRemaining, true);
                        predictions[attack.target.getPlanetID()].addIncomingFleet(1, shipsToSend, attack.takeOverTurn, false);

                        if (DEBUG_ATTACK) {
                            log.print("New prediction for planet " + attacker.getFirst() + ": ");

                            Prediction2 p = predictions[attacker.getFirst().getPlanetID()];
                            int n = 20;

                            for (int i = 0; i < n - 1; i++) {
                                log.print(p.getNumShipsAt(i, true) + ", ");
                            }

                            log.println(p.getNumShipsAt(n - 1, true));

                            log.println("Planet's surplus: " + p.getSurplusAt(0));
                        }

                        if (shipsNeeded <= 0) {
                            break;
                        }
                    }

                    // Update predictions for the target
                    predictions[attack.target.getPlanetID()].computePredictions();
                }
            }
        }
    }

    private static Attack findBestAttack(DuelPlanetWars pw, Prediction2[] predictions, Planet target) {
        Set<Integer> turns = new HashSet<Integer>();

        for (Entry<Integer, Force> entry : predictions[target.getPlanetID()].getIncomingForces().entrySet()) {
            if (entry.getValue().ships1 > 0) {
                // We want to coordinate this attack
                turns.add(entry.getKey());
            }

            if (entry.getValue().ships2 > 0) {
                // We want to snipe the planet
                turns.add(entry.getKey() + 1);
            }
        }

        for (Planet planet : pw.getMyPlanets()) {
            // We might want to attack from this planet
            turns.add(pw.getDistance(target, planet));
        }

        Attack bestAttack = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (Integer t : turns) {
            Attack attack = findAttack(pw, predictions, target, t);

            if (attack != null) {
                attack.computeScore(predictions);

                if (attack.score > bestScore) {
                    bestAttack = attack;
                    bestScore = attack.score;
                }
            }
        }

        return bestAttack;
    }

    private static Attack findAttack(DuelPlanetWars pw, Prediction2[] predictions, Planet target, int turn) {
        int shipsAvailable = 0;
        int closerEnemies = 0;
        List<Planet> attackers = new ArrayList<Planet>();

        for (Planet planet : pw.getClosestPlanets(target)) {
            int dist = pw.getDistance(target, planet);

            if (dist <= turn) {
                if (planet.getOwner() == 1) { // TODO: check predictions to coordinate with planets that will be mine?
                    int surplus = getAttackSurplus(pw, predictions, planet, target, turn - dist);

                    if (surplus > 0) {
                        shipsAvailable += surplus;
                        attackers.add(planet);
                    }
                }
                if (dist < turn && predictions[planet.getPlanetID()].getOwnerAt(turn - dist) == 2) {
                    closerEnemies += predictions[planet.getPlanetID()].getNumShipsAt(turn - dist, false);
                }
            } else {
                break;
            }
        }

        if (shipsAvailable > 0) {
            int shipsNeeded = predictions[target.getPlanetID()].getAttackForceRequiredAt(turn);

            // Figure out how many ships I need in order to defend against snipes:
            // The enemy can send forces from any planet that is at distance turn or less.
            // I should expect that many ships the turn after I take the planet.

            int additionalShipsNeeded = 0;

            for (Planet sniper : pw.getClosestPlanets(target)) {
                int dist = pw.getDistance(target, sniper);

                if (dist > turn) {
                    break;
                }

                if (predictions[sniper.getPlanetID()].getOwnerAt(turn - dist + 1) == 2) {
                    // This is an enemy planet that could snipe this target
                    additionalShipsNeeded += predictions[sniper.getPlanetID()].getNumShipsAt(turn - dist + 1);
                }
            }

            // We need additionalShipsNeeded more ships to ensure that we don't lose the planet to a snipe

            if (shipsAvailable >= shipsNeeded) {
                return new Attack(target, attackers, turn, closerEnemies, shipsNeeded, shipsAvailable, additionalShipsNeeded);
            }
        }

        return null;
    }

    private static int getAttackSurplus(DuelPlanetWars pw, Prediction2[] predictions, Planet attacker, Planet target, int t) {
        // TODO: Optimization - don't add or remove fleets, it's slow
        int surplus = predictions[attacker.getPlanetID()].getSurplusAt(t);

        if (DEBUG_ATTACK) {
            log.println("Computing surplus for planet " + attacker.getPlanetID() + " attacking planet " + target.getPlanetID() + " at turn " + t);
        }

        if (surplus > 0) {
            // The attacking planet should be able to survive the closest enemy planet's attack, counting reinforcements
            // Find the closest enemy planet that is not the target of the attack
            int closestEnemyDist = -1;
            int closestEnemyShips = 0;

            boolean skipTarget = target.getGrowthRate() >= attacker.getGrowthRate();

            for (Planet planet : pw.getClosestPlanets(attacker)) {
                if (planet.getOwner() == 2 && !(skipTarget && planet == target)) {
                    closestEnemyDist = pw.getDistance(planet, attacker);
                    closestEnemyShips = planet.getNumShips();
                    break;
                }
            }

            if (DEBUG_ATTACK) {
                log.println("closestEnemyDist: " + closestEnemyDist + " closestEnemyShips: " + closestEnemyShips);
            }

            if (closestEnemyDist >= 0) {
                // Count the available reinforcements
                int reinforcements = 0;

                for (Planet planet : pw.getClosestPlanets(attacker)) {
                    int dist = pw.getDistance(attacker, planet);

                    if (dist >= closestEnemyDist) {
                        break;
                    } else if (predictions[planet.getPlanetID()].getOwnerAt(closestEnemyDist - dist) == 1) {
                        reinforcements += predictions[planet.getPlanetID()].getSurplusAt(closestEnemyDist - dist);
                        if (DEBUG_ATTACK) {
                            log.println("Reinforcer: " + planet);
                            log.println("Total reinforcements: " + reinforcements);
                        }
                    }
                }

                if (reinforcements < closestEnemyShips) {
                    // See how many ships we have left to send if the enemy decides to attack and we defend.
                    // This uses the predictions to take into account all incoming fleets on this planet.
                    predictions[attacker.getPlanetID()].addIncomingFleet(2, closestEnemyShips - reinforcements, closestEnemyDist, true);
                    surplus = predictions[attacker.getPlanetID()].getSurplusAt(t);
                    predictions[attacker.getPlanetID()].removeIncomingFleet(2, closestEnemyShips - reinforcements, closestEnemyDist, true);
                }
            }
        } else {
            // Suppose we send out all our current ships, would that make any difference in how long this planet stays ours?
        }

        if (DEBUG_ATTACK) {
            log.println("Resulting surplus: " + surplus);
        }

        return surplus;
    }

    private static void supply(DuelPlanetWars pw, Prediction2[] predictions) {
        Set<Planet> frontier = findFrontier(pw, predictions);

        if (DEBUG_SUPPLY) {
            log.print("Frontier: ");
            for (Planet planet : frontier) {
                log.print(planet.getPlanetID() + ", ");
            }
            log.println();
        }

        if (frontier.isEmpty()) {
            // No frontier means we can't supply anything
            return;
        }

        for (Planet planet : pw.getMyPlanets()) {
            Planet supplyTarget = null;

            if (frontier.contains(planet)) {
                // See if there is a better frontier planet we might want to supply
                int closestEnemyDist = -1;
                Planet closestEnemy = null;

                for (Planet p : pw.getClosestPlanets(planet)) {
                    if (predictions[p.getPlanetID()].getOwnerAt(MAX_TURNS - turn) == 2) {
                        closestEnemyDist = pw.getDistance(p, planet);
                        closestEnemy = p;
                        break;
                    }
                }

                if (closestEnemy != null) {
                    for (Planet p : frontier) {
                        if (pw.getDistance(p, closestEnemy) < closestEnemyDist && pw.getDistance(p, planet) < closestEnemyDist) {
                            supplyTarget = p;
                            break;
                        }
                    }
                }
            } else {
                // Find the closest frontier planet
                for (Planet p : pw.getClosestPlanets(planet)) {
                    if (frontier.contains(p)) { // Testing for ownership here isn't necessary. This will be done when selecting the best relay.
                        supplyTarget = p;
                        break;
                    }
                }
            }

            if (DEBUG_SUPPLY) {
                log.println("Supply target for planet " + planet.getPlanetID() + ": " + (supplyTarget == null ? null : supplyTarget.getPlanetID()));
            }

            if (supplyTarget != null) {
                // See if there are intermediate planets we can route through without it costing too much time
                int supplyDist = pw.getDistance(planet, supplyTarget);
                int maxDist = (int) Math.ceil(1.2 * supplyDist);
                int bestScore = Integer.MAX_VALUE;
                Planet bestRelay = null;

                for (Planet p : pw.getClosestPlanets(planet)) {
                    int distPToSupply = pw.getDistance(p, supplyTarget);
                    int distToP = pw.getDistance(planet, p);

                    if (distPToSupply < supplyDist && distToP <= supplyDist && distPToSupply + distToP <= maxDist && predictions[p.getPlanetID()].getOwnerAt(distToP) == 1) {
                        // This is a potential intermediate stop
                        int score = distPToSupply * distPToSupply + distToP * distToP; // TODO: fine-tune the score?

                        if (score < bestScore) {
                            bestScore = score;
                            bestRelay = p;
                        }
                    }

                    if (p == supplyTarget) {
                        // We have seen all closer planets
                        break;
                    }
                }

                if (DEBUG_SUPPLY) {
                    log.println("Best relay: " + (bestRelay == null ? null : bestRelay.getPlanetID()));
                }

                if (bestRelay != null) {
                    // Send stuff to the best relay
                    int surplus = predictions[planet.getPlanetID()].getSurplusAt(0);

                    if (surplus > 0) {
                        pw.issueOrder(planet, bestRelay, surplus);
                        // TODO: updates might not be needed
                        predictions[planet.getPlanetID()].addOutgoingFleet(1, surplus, 0, true);
                        predictions[bestRelay.getPlanetID()].addIncomingFleet(1, surplus, pw.getDistance(planet, bestRelay), true);
                    }
                }
            }
        }
    }

    /**
     * Determines which planets make up the frontier, but doesn't send any ships yet.
     * @param pw
     * @param predictions
     * @return
     */
    private static Set<Planet> findFrontier(DuelPlanetWars pw, Prediction2[] predictions) {
        // All planets that don't have other planets that are closer to them than the nearest enemy planet are automatically in the frontier
        Set<Planet> frontier = new HashSet<Planet>(pw.getNumPlanets());

        for (Planet mine : pw.getPlanets()) {
            if (predictions[mine.getPlanetID()].getOwnerAt(MAX_TURNS - turn) == 1) {
                int closestAlly = -1;

                for (Planet planet : pw.getClosestPlanets(mine)) {
                    int dist = pw.getDistance(mine, planet);

                    if (closestAlly > -1) {
                        if (dist > closestAlly) {
                            // This planet has an ally that is strictly closer than the closest enemy planet; it is not necessarily in the frontier
                            break;
                        }
                    } else {
                        if (predictions[planet.getPlanetID()].getOwnerAt(MAX_TURNS - turn) == 1) {
                            closestAlly = dist;
                        }
                    }

                    if (predictions[planet.getPlanetID()].getOwnerAt(MAX_TURNS - turn) == 2) {
                        // There is no strictly closer allied planet (else we would have left the loop before): this planet is on the frontier
                        frontier.add(mine);
                        break;
                    }
                }
            }
        }

        // Greedy frontier selection. Makes sure that every non-frontier planet has a frontier planet that is closer than the closest enemy planet.
        // For every non-frontier planet:
        // 1. If there is a frontier planet that is closer than any enemy planet, we're good
        // 2. Otherwise, add the allied planet closest to the closest enemy planet that is still closer to me to the frontier
        // 3. If there is no such planet, I am a frontier planet
        for (Planet planet : pw.getPlanets()) {
            if (!frontier.contains(planet) && predictions[planet.getPlanetID()].getOwnerAt(MAX_TURNS - turn) == 1) {
                Planet closestEnemy = null;
                int closestEnemyDist = -1;

                for (Planet p : pw.getClosestPlanets(planet)) {
                    if (frontier.contains(p)) {
                        break;
                    } else if (predictions[p.getPlanetID()].getOwnerAt(MAX_TURNS - turn) == 2) {
                        closestEnemy = p;
                        closestEnemyDist = pw.getDistance(p, planet);
                        break;
                    }
                }

                if (closestEnemy != null) {
                    // We encountered an enemy planet before finding a suitable frontier planet to send ships to.
                    // Find the allied planet closest to the enemy planet that is still closer to me and add it to the frontier.
                    int minDistToEnemy = closestEnemyDist;
                    Planet supplyTarget = planet;

                    for (Planet p : pw.getClosestPlanets(planet)) {
                        if (pw.getDistance(p, planet) >= closestEnemyDist) {
                            // We've seen all possible candidates, use the best.
                            frontier.add(supplyTarget);
                            break;
                        }

                        if (predictions[p.getPlanetID()].getOwnerAt(MAX_TURNS - turn) == 1) {
                            // This planet is a possible supply target and it's closer to me than the closest enemy, only thing left is to compare distance to the enemy
                            int dist = pw.getDistance(p, closestEnemy);

                            if (dist < minDistToEnemy) {
                                minDistToEnemy = dist;
                                supplyTarget = p;
                            }
                        }
                    }
                }
            }
        }

        return frontier;
    }

    private static void rage(DuelPlanetWars pw, final Prediction2[] predictions) {
        // Send all surplus to the closest enemy planet
        for (Planet planet : pw.getMyPlanets()) {
            int surplus = predictions[planet.getPlanetID()].getSurplusAt(0);

            if (surplus > 0) {
                for (Planet target : pw.getClosestPlanets(planet)) {
                    int dist = pw.getDistance(planet, target);

                    if (predictions[target.getPlanetID()].getOwnerAt(MAX_TURNS - turn) == 2 && predictions[target.getPlanetID()].getOwnerAt(dist - 1) != 0) {
                        pw.issueOrder(planet, target, surplus);
                        break;
                    }
                }
            }
        }
    }
}
