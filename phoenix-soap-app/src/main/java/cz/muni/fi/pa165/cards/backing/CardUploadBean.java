package cz.muni.fi.pa165.cards.backing;

import cz.muni.fi.pa165.cards.db.Category;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

import java.util.Iterator;
import java.util.Set;
import javax.annotation.PostConstruct;

import org.apache.commons.io.IOUtils;
import org.richfaces.event.FileUploadEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import cz.muni.fi.pa165.cards.db.Card;
import cz.muni.fi.pa165.cards.db.User;
import cz.muni.fi.pa165.cards.helpers.LocUtil;
import cz.muni.fi.pa165.cards.managers.CardManager;
import cz.muni.fi.pa165.cards.managers.CategoryManager;
import cz.muni.fi.pa165.cards.managers.ImageManager;
import cz.muni.fi.pa165.cards.managers.UserManager;
import cz.muni.fi.pa165.cards.models.CategoryModelWrapper;
import cz.muni.fi.pa165.cards.models.CategoryViewModel;
import java.util.ArrayList;
import java.util.HashSet;
import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.context.FacesContext;
import org.springframework.context.annotation.Scope;

@ManagedBean
@ViewScoped
@Scope("view")
public class CardUploadBean implements Serializable {
    private static final long serialVersionUID = 221133333L;
    private static final Logger log = LoggerFactory.getLogger(CardUploadBean.class);

    @Autowired
    protected transient UserManager userManager;

    @Autowired
    protected transient CategoryManager categoryManager;

    @Autowired
    protected transient CardManager cardManager;

    @Autowired
    protected transient ImageManager imageManager;

    @Autowired
    protected transient UserBean userBean;

    protected boolean editMode=false;
    protected Card card;
    protected String text;
    protected byte[] frontImage;
    protected byte[] backImage;
    protected ArrayList<CategoryModelWrapper> categories;
    protected CategoryModelWrapper currentCategory;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Card getCard() {
        return card;
    }

    public void setCard(Card card) {
        System.out.println(">>>>>>>>>>clicked");
        this.card = card;
    }

    public byte[] getFrontImage() {
        return frontImage;
    }

    public void setFrontImage(byte[] frontImage) {
        this.frontImage = frontImage;
    }

    public byte[] getBackImage() {
        return backImage;
    }

    public void setBackImage(byte[] backImage) {
        this.backImage = backImage;
    }

    public void setCardManager(CardManager cardManager) {
        this.cardManager = cardManager;
    }

    public void setCategoryManager(CategoryManager categoryManager) {
        this.categoryManager = categoryManager;
    }

    public void setUserManager(UserManager userManager) {
        this.userManager = userManager;
    }

    /**
     * Adding new card using card manager
     * @return
     */
    public String addCard(){
        try{
            User loggedUser = userBean.getLoggedUser();
        	card.setOwner(userManager.getUserByID(loggedUser.getId()));
                
            if (this.editMode){
                return this.updateCard();
            }
                
            // get categories displayed
            if (this.categories!=null){
                Iterator<CategoryModelWrapper> iterator = this.categories.iterator();
                while(iterator.hasNext()){
                    CategoryModelWrapper curCat = iterator.next();
                    if (curCat==null || curCat.getCat()==null){
                        log.error("Category in list to associate with card was null");
                        continue;
                    }
                    
                    // load right db entity from database
                    Category catEntity = this.categoryManager.findById(curCat.getCat().getId());
                    if (catEntity==null || catEntity.getOwner().equals(loggedUser)==false){
                        LocUtil.sendMessage("cardForm:addCardCategories", 
                            FacesMessage.SEVERITY_WARN, "Incorrect category selected ["+curCat.getCat().getName()+"]", "");
                        continue;
                    }
                    
                    // associate to given category
                    card.addToCategory(catEntity);
                }
            }
            
            cardManager.addCard(card);
            userManager.addCard(card, loggedUser.getId() );
            imageManager.addImages(frontImage, backImage, card);
            
            LocUtil.sendMessage(null, FacesMessage.SEVERITY_INFO, 
                    LocUtil.getMessageFromDefault("cardAddOK"), "");
            
        } catch(Exception e){
            log.error("Exception during category add procedure", e);
            
            LocUtil.sendMessage(null, FacesMessage.SEVERITY_ERROR, 
                    LocUtil.getMessageFromDefault("cardAddFail"), e.getLocalizedMessage());
            
            return "EXCEPTION";
        } finally{
            resetFields();
        }
        return "desktopHome";
    }

    /**
     * Method updating card to correspond view state on edit page
     * Updates static parameters, categories and if are images uploaded, they are
     * changed as well.
     * @return 
     */
    public String updateCard(){
        try{
            User loggedUser = this.userBean.getLoggedUser();
                    
            // need to get fresh card
            Card freshCard = this.cardManager.getCardById(card.getId());
            // update all data from form
            freshCard.setAddress(card.getAddress());
            freshCard.setCompany(card.getCompany());
            freshCard.setDescription(card.getDescription());
            freshCard.setEmail(card.getEmail());
            freshCard.setFirstName(card.getFirstName());
            freshCard.setPublicVisibility(card.getPublicVisibility());
            freshCard.setSecondName(card.getSecondName());
            freshCard.setTelephone(card.getTelephone());
            Set<Category> freshCategories = freshCard.getCategories();
            Set<Category> newFreshCategories = new HashSet<Category>(this.categories==null ? 0 : this.categories.size());
            
            // get categories displayed
            if (this.categories!=null){
                Iterator<CategoryModelWrapper> iterator = this.categories.iterator();
                while(iterator.hasNext()){
                    CategoryModelWrapper curCat = iterator.next();
                    if (curCat==null || curCat.getCat()==null){
                        log.error("Category in list to associate with card was null");
                        continue;
                    }
                    
                    // load right db entity from database
                    Category catEntity = this.categoryManager.findById(curCat.getCat().getId());
                    
                    // is already in set?
                    if(freshCategories.contains(catEntity)){
                        newFreshCategories.add(catEntity);
                        continue;
                    }
                    
                    // security check
                    if (catEntity==null){
                        LocUtil.sendMessage("cardForm:addCardCategories", 
                            FacesMessage.SEVERITY_WARN, "Incorrect category selected (NULL) ["+curCat.getCat().getName()+"]", "");
                        
                        log.error("Empty category loaded");
                        continue;
                    }
                    
                    if (catEntity.getOwner().getId().equals(loggedUser.getId())==false){
                        LocUtil.sendMessage("cardForm:addCardCategories", 
                            FacesMessage.SEVERITY_WARN, "Incorrect category selected ["+curCat.getCat().getName()+"]", "");
                        
                        log.error("Cheater here", catEntity);
                        continue;
                    }
                    
                    
                    
                    // associate to given category
                    freshCard.addToCategory(catEntity);
                    newFreshCategories.add(catEntity);
                }
                // now remove old categories
                Iterator<Category> iterator1 = freshCategories.iterator();
                while(iterator1.hasNext()){
                    Category cCat = iterator1.next();
                    
                    // is in allowed?
                    if (newFreshCategories.contains(cCat)){
                        // ok is in allowed set
                        continue;
                    }
                    
                    // now remove
                    freshCard.removeFromCategory(cCat);
                }
            }
            
            cardManager.updateCard(freshCard);
            if (frontImage != null || backImage != null) {
                imageManager.addImages(
                        (frontImage == null) ? null : frontImage,
                        (backImage == null) ? null : backImage,
                        freshCard
                );
            }
            
            LocUtil.sendMessage(null, FacesMessage.SEVERITY_INFO, 
                    LocUtil.getMessageFromDefault("cardUpdateOK"), "");
            log.info("Updating card..");
            return "updated";
            
        } catch(Exception e){
            log.error("Exception during category edit", e);
            LocUtil.sendMessage(null, FacesMessage.SEVERITY_ERROR, 
                    LocUtil.getMessageFromDefault("cardUpdateFail"), e.getLocalizedMessage());
            
            return "EXCEPTION";
        } finally{
            resetFields();
        }
    }
    
    /**
     * Action for editing card
     */
    public String editCard(long id){
        log.info("Editing card: " + id);
        return "CARD_EDITED";
    }

    public void uploadFront(FileUploadEvent event) {
        System.out.println("uf");
        frontImage = event.getUploadedFile().getData();
        System.out.println("uf");
    }

    public void uploadBack(FileUploadEvent event) {
        backImage = event.getUploadedFile().getData();
    }

    public void paintFront(final OutputStream stream, final Object object) throws IOException {
        paint(stream, frontImage);
    }

    public void paintBack(final OutputStream stream, final Object object) throws IOException {
        paint(stream, backImage);
    }

    public void paint(OutputStream stream, byte[] data) throws IOException {
        if (data == null) {
            data  = IOUtils.toByteArray(Thread.currentThread().getContextClassLoader().getResourceAsStream("/blank.gif"));
            log.info("Null data");
        }
        
        stream.write(data);
        stream.close();
        log.info("Painted finally");
    }

    public void resetFields() {
        this.card = new Card();
        frontImage = null;
        backImage = null;
        
        this.categories = new ArrayList<CategoryModelWrapper>();
        this.currentCategory = null;
    }

    @PostConstruct
    public void initBean(){
        this.resetFields();

        log.info("Initializing bean");
        FacesContext facesContext = FacesContext.getCurrentInstance();
        String id = facesContext.getExternalContext().getRequestParameterMap().get("id");
        if (id!=null){
            log.info("ID not null: " + id);
            try{
                Long cardId2edit = Long.parseLong(id);
                Card cardById = this.cardManager.getCardById(cardId2edit);
                if (cardById==null){
                    log.error("Cannot load specified card ID: " + cardId2edit);
                    return;
                }
                
                if (cardById.getOwner().equals(this.userBean.getLoggedUser())==false){
                    log.error("Invalid card id! Card owner: " + cardById.getOwner() + "; logged: " + this.userBean.getLoggedUser(), cardById);
                    throw new IllegalArgumentException("Trying to cheat.");
                }
                
                this.editMode=true;
                this.card = cardById;
                
                // load categories for card
                this.loadCategories4card();
                
//                // load images
//                Image front = (card.getImages()).get(ImageVariant.FRONT_SMALL);
//                Image back = (card.getImages()).get(ImageVariant.BACK_SMALL);
//
//                if (front != null) {
//                        this.frontImage = front.getPicture();
//                } 
//                
//                if (back != null) {
//                        this.backImage = back.getPicture();
//                }
                
            } catch(Exception e){
                log.error("Error when trying to load card to edit", e);
            }
        }
    }
    
    /**
     * Loads categories associated to card to specific list to be visible on page
     * (and editable)
     */
    public void loadCategories4card(){
        if (this.card==null){
            return;
        }
        
        Set<Category> categories1 = this.card.getCategories();
        Iterator<Category> iterator = categories1.iterator();
        while(iterator.hasNext()){
            Category cat = iterator.next();
            if (cat==null){
                continue;
            }

            // build category view model
            CategoryViewModel viewCat = new CategoryViewModel();
            viewCat.initFrom(cat);
            
            CategoryModelWrapper curWrapper = new CategoryModelWrapper(viewCat);
            curWrapper.setPath2root(this.categoryManager.generateTextualPath2root(cat));
            
            this.categories.add(curWrapper);
        }
    }

    /**
     * Action adds category to selection for current card
     */
    public void addCategory(){
        if (this.currentCategory==null){
            LocUtil.sendMessage("cardForm:addCardCategories", 
                    FacesMessage.SEVERITY_WARN, "Please choose some category", "");
            return;
        }
        // check for duplicities
        Iterator<CategoryModelWrapper> iterator = this.categories.iterator();
        while(iterator.hasNext()){
            CategoryModelWrapper next = iterator.next();
            if (next==null || next.getCat()==null){
                iterator.remove();
                continue;
            }
            
            if (next.equals(this.currentCategory)){
                this.currentCategory = null;
                LocUtil.sendMessage("cardForm:addCardCategories", 
                        FacesMessage.SEVERITY_WARN, "Category already added to selection", "");
                
                return;
            }
        }
        
        this.categories.add(this.currentCategory);
        this.currentCategory = null;
    }
    
    /**
     * Delete category from selection
     * @param number 
     */
    public void deleteCategory(int number){
        if (this.categories.size() <= number){
            return;
        }
        
        this.categories.remove(number);
    }

    public ArrayList<CategoryModelWrapper> getCategories() {
        return categories;
    }

    public void setCategories(ArrayList<CategoryModelWrapper> categories) {
        this.categories = categories;
    }

    public CategoryModelWrapper getCurrentCategory() {
        return currentCategory;
    }

    public void setCurrentCategory(CategoryModelWrapper currentCategory) {
        this.currentCategory = currentCategory;
    }

    public boolean isEditMode() {
        return editMode;
    }

    public void setEditMode(boolean editMode) {
        log.info("/////////////////////////////Set edit mode right now");
        this.editMode = editMode;
    }
    
    
}