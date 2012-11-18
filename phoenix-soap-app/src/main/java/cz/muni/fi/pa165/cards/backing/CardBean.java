package cz.muni.fi.pa165.cards.backing;

import cz.muni.fi.pa165.cards.db.Category;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import java.util.Set;
import javax.faces.component.UIOutput;
import javax.faces.context.FacesContext;
import javax.faces.event.AjaxBehaviorEvent;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import cz.muni.fi.pa165.cards.db.Card;
import cz.muni.fi.pa165.cards.db.Image;
import cz.muni.fi.pa165.cards.db.ImageVariant;
import cz.muni.fi.pa165.cards.db.User;
import cz.muni.fi.pa165.cards.helpers.LocUtil;
import cz.muni.fi.pa165.cards.managers.CardManager;
import cz.muni.fi.pa165.cards.managers.CategoryManager;
import cz.muni.fi.pa165.cards.managers.UserManager;
import java.util.HashSet;
import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import org.springframework.context.annotation.Scope;

public class CardBean implements Serializable {

	private static final long serialVersionUID = 2222233333L;

	private static final Logger log = LoggerFactory.getLogger(CardBean.class);

	@Autowired
	protected transient UserManager userManager;

	@Autowired
	protected transient CategoryManager categoryManager;

	@Autowired
	protected transient CardManager cardManager;
        
        @Autowired
        protected transient CardUploadBean cardUploadBean;

	@Autowired
	protected UserBean userBean;

	@PersistenceContext
	protected EntityManager em;

	private List<Card> cards = null;

	// private List<Card> privateCards = null;

	private List<CardInfo> cardInfo = null;

	private Card selectedCard = null;

	private boolean renderedSelected = false;

	private String tempImage = "vizitka.jpg";

	private String selectedImage = null;

	private UIOutput outputTextSmall = null;

	private String searchText = null;

	private boolean onlyUserCards = true;
        
        private boolean canEditSelected = false;

	public class CardInfo {

		private String info;

		public CardInfo(String inf) {
			info = inf;
		}

		public String getInfo() {
			return info;
		}

		public void setInfo(String inf) {
			info = inf;
		}
	}

	@SuppressWarnings("unchecked")
	public List<Card> getCards() {

		cards = new ArrayList<Card>();

		// is somebody logged in?
		if (userBean.isLoggedIn()) {

			User user = userBean.getLoggedUser();
			// is he searching for some cards ?
			if (searchText != null) {

				if (onlyUserCards) {
					cards.addAll( cardManager.fulltextSearchInUserCards(searchText, user) );
					System.err.println("####### User logged - only user cards search" + cards);
				} else {
					cards.addAll( cardManager.fulltextSearchInPublicCards(searchText));
					System.err.println("####### User logged - all cards search" + cards);
				}

			} else {

				if (onlyUserCards) {
					cards.addAll( (List<Card>) cardManager.getUserCards(user) );
					System.err.println("####### User logged - only private" + cards);
				} else {
					List<Card> publicCards = (List<Card>) cardManager.getPublicCards();
					List<Card> userCards = (List<Card>) cardManager.getUserCards(user);
					publicCards.removeAll( userCards );
					publicCards.addAll(userCards);
					cards.addAll( publicCards );
					System.err.println("####### User logged - private and public" + cards);
				}
			}

		} else { // if is nobody logged in

			if (searchText != null) {
				cards.addAll( cardManager.fulltextSearchInPublicCards(searchText));
				System.err.println("####### User NOT logged - public cards search" + cards);
			} else {
				cards.addAll( (List<Card>) cardManager.getPublicCards() );
				System.err.println("####### User NOT logged - all public cards" + cards);
			}
		}

		return cards;
	}

	public Long getRenderedCardSmallImage() {

		String idOfRenderedCard = outputTextSmall.getValue().toString();

		Long id = Long.valueOf(idOfRenderedCard);

		if (cards != null) {

			for (Card i : cards) {

				if (i.getId().equals(id)) {
					Image front = (i.getImages()).get(ImageVariant.FRONT_SMALL);
					Image back = (i.getImages()).get(ImageVariant.BACK_SMALL);

					if (front != null) {
						return front.getId();
					} else if (back != null) {
						return back.getId();
					}

					return -1L;
				}
			}
		}

		return id;
	}

	public Long getSelectedCardMediumImage() {

		Image front = this.selectedCard.getImages().get(
				ImageVariant.FRONT_MEDIUM);
		Image back = this.selectedCard.getImages()
				.get(ImageVariant.BACK_MEDIUM);

		if (front != null) {
			return front.getId();
		} else if (back != null) {
			return back.getId();
		}

		return -2L;
	}
        private List<String> selectedCardMediumImages;

        public List<String> getSelectedCardMediumImages() {
            selectedCardMediumImages = new  ArrayList<String>();
            if (this.selectedCard != null){
                            Image front = this.selectedCard.getImages().get(
				ImageVariant.FRONT_MEDIUM);
                            if (front != null) {
                                 selectedCardMediumImages.add(front.getId().toString());
                             }                            
                            
                            Image back = this.selectedCard.getImages()
				.get(ImageVariant.BACK_MEDIUM);
                            if (back != null) {
                                 selectedCardMediumImages.add(back.getId().toString());
                             }                
            } else {
                selectedCardMediumImages.add("-2");
            }      
           
            return selectedCardMediumImages;
        }

    public void setSelectedCardMediumImages(List<String> selectedCardMediumImages) {
        this.selectedCardMediumImages = selectedCardMediumImages;
    }
        

       
        
        
        
//        public List<String> getSelectedCardMediumImages() {
//            selecteCardMediumImages.
//            
//            
//                Image front = this.selectedCard.getImages().get(
//				ImageVariant.FRONT_MEDIUM);
//		Image back = this.selectedCard.getImages()
//				.get(ImageVariant.BACK_MEDIUM);
//            
//                List<Image> retList = new ArrayList<Image>();
//                Long backupImage= -2l;
//                
//                if (front != null) {
//                    retList.add(front);
//                }
//                //} else {
//                //    retList.add(backupImage);
//                //} 
//                if (back != null) {
//                    retList.add(back);
//                }
//                //} else {
//                //    retList.add(backupImage);
//                //} 
//                return retList;
//	}
        
        

	public List<CardInfo> getCardInfo() {

		if (selectedCard != null) {

			cardInfo = new ArrayList<CardInfo>();

			String firstName = selectedCard.getFirstName();
			if (firstName != null) {
				cardInfo.add(new CardInfo("First name: " + firstName));
			}

			String surName = selectedCard.getSecondName();
			if (surName != null) {
				cardInfo.add(new CardInfo("Surname : " + surName));
			}

			String company = selectedCard.getCompany();
			if (company != null) {
				cardInfo.add(new CardInfo("Company: " + company));
			}

			String address = selectedCard.getAddress();
			if (address != null) {
				cardInfo.add(new CardInfo("Address: " + address));
			}

			String telephone = selectedCard.getTelephone();
			if (telephone != null) {
				cardInfo.add(new CardInfo("Telephone: " + telephone));
			}

			String email = selectedCard.getEmail();
			if (email != null) {
				cardInfo.add(new CardInfo("Email: " + email));
			}

			/*
			 * Map<String, String> properties = selectedCard.getProperties();
			 * if( properties != null ) { for(String i : properties.keySet()) {
			 * cardInfo.add( new CardInfo(i + ": " + properties.get(i)) ); } }
			 */

			String description = selectedCard.getDescription();
			if (description != null) {
				cardInfo.add(new CardInfo("Description: " + description));
			}
                        
                        // get associated categories
                        Set<Category> categories = selectedCard.getCategories();
                        if (categories!=null && categories.isEmpty()==false){
                            log.info("Processing categories: " + categories.size());
                            cardInfo.add(new CardInfo("Categories: "));
                            
                            // load again
                            Iterator<Category> iterator = categories.iterator();
                            while(iterator.hasNext()){
                                Category cat = iterator.next();
                                if (cat==null){
                                    log.error("Null category associated");
                                    continue;
                                }
                                
                                Category freshCat = this.categoryManager.findById(cat);
                                if (freshCat==null){
                                    log.error("Null category associated");
                                    continue;
                                }
                                
                                // get path2root, remove private categories and print
                                List<Category> path2root = this.categoryManager.getPath2root(freshCat);
                                CategoryManager.removePrivateSubtreeFromPath(path2root);
                                String path2rootString = this.categoryManager.generateTextualPath2root(path2root);
                                
                                cardInfo.add(new CardInfo(path2rootString));
                            }
                            
                        }
		}

		return cardInfo;
	}

        public void selectCard(){
            FacesContext context = FacesContext.getCurrentInstance();
            String cardId = context.getExternalContext().getRequestParameterMap().get("selectedImageId");
            if (cardId==null){
                log.error("Cannot load empty card id");
                return;
            }
            
            try{
                Long cardIdLong = Long.parseLong(cardId);
                for (Card i : cards) {
			if (i.getId().equals(Long.valueOf(cardIdLong))) {
				this.setSelectedCard(i);
				selectedImage = tempImage;
				renderedSelected = true;
                                log.info("Card selected: id=" + cardIdLong);
                                
                                // can edit??
                                User loggedUser = this.userBean.getLoggedUser();
                                if (loggedUser!=null){
                                    this.canEditSelected=this.selectedCard.getOwner().getId().equals(loggedUser.getId());
                                } else {
                                    this.canEditSelected=false;
                                }
                                
                                break;
			}
		}
                
            } catch(Exception e){
                log.error("Error during loading card", e);
            }
        }
        
	public void selectCard(AjaxBehaviorEvent event) {

		FacesContext context = FacesContext.getCurrentInstance();
		HttpServletRequest myRequest = (HttpServletRequest) context
				.getExternalContext().getRequest();
                
                
                
		String id = myRequest.getParameter("selectedImageId");

		for (Card i : cards) {
			if (i.getId().equals(Long.valueOf(id))) {
				this.setSelectedCard(i);
				selectedImage = tempImage;
				renderedSelected = true;
			}
		}

	}
        
        @PostConstruct
        public void init(){
            log.info("#$$$$$$$$$$$$$$$$$$$$$$ Class initialized");
        }
        
        /**
         * Triggers redirect to card edit
         * @return JSF
         */
        public String editCard(){
            this.cardUploadBean.setEditMode(true);
            this.cardUploadBean.card = this.selectedCard;
            log.error("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$ EDIT returned");
            return "edit?faces-redirect=true&IncludeViewParams=true";
        }
        
        public String remove() {
            // removes currently selected item
            if (this.selectedCard==null){
                log.error("Cannot delete empty card");
                return "/welcome.xhtml";
            }
            
            // reload fresh
            Card freshCard = this.cardManager.getCardById(this.selectedCard.getId());
            if (freshCard==null || freshCard.getOwner().equals(this.userBean.getLoggedUser())==false){
                log.error("Card is null or does not belongs to correct user", freshCard);
                return "/welcome.xhtml";
            }
            
            try {
                // removing from parent
                User loggedUser = this.userBean.getLoggedUser();
                User persistenceUser = this.userManager.getUserByID(loggedUser.getId());
                log.info("Deleting freshCard for user: " + freshCard.getId(), freshCard);
                persistenceUser.removeCard(freshCard);
                this.userManager.updateUser(persistenceUser);
                this.cardManager.deleteCard(freshCard);
                
                LocUtil.sendMessage(null, FacesMessage.SEVERITY_INFO, 
                        LocUtil.getMessageFromDefault("cardDeletedOK"), "");
                
            } catch(Exception e){
                log.error("Exception thrown where removing Card", e);
                
                LocUtil.sendMessage(null, FacesMessage.SEVERITY_ERROR, 
                        LocUtil.getMessageFromDefault("cardDeletedFail"), e.getLocalizedMessage());
                
                return "/welcome.xhtml";
            }
            
            return "/welcome.xhtml";
        }
 

	public void setPublicCards(List<Card> cards) {
		this.cards = cards;
	}

	public String getTempImage() {
		return tempImage;
	}

	public Card getSelectedCard() {
		return selectedCard;
	}

	public void setSelectedCard(Card card) {
		this.selectedCard = card;
	}

	public void setTempImage(String tempImage) {
		this.tempImage = tempImage;
	}

	public String getSelectedImage() {
		return selectedImage;
	}

	public void setSelectedImage(String selectedImage) {
		this.selectedImage = selectedImage;
	}

	public boolean isRenderedSelected() {
		return renderedSelected;
	}

	public void setRenderedSelected(boolean renderedSelected) {
		this.renderedSelected = renderedSelected;
	}

	public UserManager getUserManager() {
		return userManager;
	}

	public void setUserManager(UserManager userManager) {
		this.userManager = userManager;
	}

	public CategoryManager getCategoryManager() {
		return categoryManager;
	}

	public void setCategoryManager(CategoryManager categoryManager) {
		this.categoryManager = categoryManager;
	}

	public CardManager getCardManager() {
		return cardManager;
	}

	public void setCardManager(CardManager cardManager) {
		this.cardManager = cardManager;
	}

	public EntityManager getEm() {
		return em;
	}

	public void setEm(EntityManager em) {
		this.em = em;
	}

	public void setCardInfo(List<CardInfo> ci) {
		this.cardInfo = ci;
	}

	public UIOutput getOutputText() {
		return outputTextSmall;
	}

	public void setOutputText(UIOutput outputText) {
		this.outputTextSmall = outputText;
	}

	private void waitFor10Seconds() {
		Long start = System.currentTimeMillis();
		Long end = start + 10000;
		while (start < end) {
			start = System.currentTimeMillis();
		}
	}

	public String getSearchText() {
		return searchText;
	}

	public void setSearchText(String searchText) {
		this.searchText = searchText;
	}

	public boolean isOnlyUserCards() {
		return onlyUserCards;
	}

	public void setOnlyUserCards(boolean onlyUserCards) {
		this.onlyUserCards = onlyUserCards;
	}

        public void setCardUploadBean(CardUploadBean cardUploadBean) {
            this.cardUploadBean = cardUploadBean;
        }

    public boolean isCanEditSelected() {
        return canEditSelected;
    }

    public void setCanEditSelected(boolean canEditSelected) {
        this.canEditSelected = canEditSelected;
    }

}
