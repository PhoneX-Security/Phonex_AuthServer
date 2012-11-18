/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.muni.fi.pa165.cards.helpers;

import javax.faces.event.PhaseEvent;
import javax.faces.event.PhaseId;
import javax.faces.event.PhaseListener;

public class LifeCycleListener implements PhaseListener {

    @Override
    public PhaseId getPhaseId() {
        return PhaseId.ANY_PHASE;
    }

    @Override
    public void beforePhase(PhaseEvent event) {
        System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!START PHASE " + event.getPhaseId());
    }

    @Override
    public void afterPhase(PhaseEvent event) {
        System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!END PHASE " + event.getPhaseId());
    }

}