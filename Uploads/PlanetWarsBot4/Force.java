/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Sander
 */
public class Force {

    public int ships1;
    public int ships2;

    public Force(int owner, int nShips) {
        if (owner == 1) {
            ships1 = nShips;
            ships2 = 0;
        } else {
            assert owner == 2;
            ships1 = 0;
            ships2 = nShips;
        }
    }

    public void addShips(int owner, int nShips) {
        if (owner == 1) {
            ships1 += nShips;
        } else {
            assert owner == 2;
            ships2 += nShips;
        }
    }

    public int getShips(int owner) {
        if (owner == 1) {
            return ships1;
        } else {
            assert owner == 2;
            return ships2;
        }
    }

    public void removeShips(int owner, int nShips) {
        if (owner == 1) {
            //assert ships1 >= nShips;
            ships1 -= nShips;
        } else {
            assert owner == 2;
            //assert ships2 >= nShips;
            ships2 -= nShips;
        }
    }

    @Override
    public String toString() {
        return "Force{" + ships1 + ", " + ships2 + '}';
    }
}
