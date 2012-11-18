package cz.muni.fi.pa165.cards.backing;

import cz.muni.fi.pa165.cards.db.Card;
import cz.muni.fi.pa165.cards.db.ImageVariant;
import cz.muni.fi.pa165.cards.managers.CardManager;
import cz.muni.fi.pa165.cards.managers.UserManager;
import org.springframework.beans.factory.annotation.Autowired;

import javax.faces.context.FacesContext;
import javax.faces.event.AjaxBehaviorEvent;
import javax.servlet.http.HttpServletRequest;
import java.io.Serializable;

public class MobileBean implements Serializable{

    @Autowired
    protected transient UserBean userBean;

    @Autowired
    protected transient CardManager cardManager;
    
    private Long selected;
    private String newLine = "<br/>";

    public Long getSelected() {
        return selected;
    }

    public void setSelected(Long selected) {
        this.selected = selected;
    }

    public String getNewLine() {
        return newLine;
    }

    public void setNewLine(String newLine) {
        this.newLine = newLine;
    }

    public void show(AjaxBehaviorEvent event) {

        FacesContext context = FacesContext.getCurrentInstance();
        HttpServletRequest myRequest = (HttpServletRequest) context
                .getExternalContext().getRequest();

        Long id = Long.parseLong(myRequest.getParameter("selected"));
        
        setSelected(cardManager.getCardById(id).getImages().get(ImageVariant.FRONT_LARGE).getId());
    }

    public void hide(AjaxBehaviorEvent event) {
        setSelected(null);
    }
    
}
