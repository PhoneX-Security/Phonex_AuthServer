package cz.muni.fi.pa165.cards.managers;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import cz.muni.fi.pa165.cards.db.Card;
import cz.muni.fi.pa165.cards.db.User;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import org.springframework.stereotype.Repository;

/**
 * 
 * @author gooseka
 * 
 */
@Repository
@Transactional
public class UserManager {

	@PersistenceContext
	private EntityManager em;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	/*
	 * Constants
	 */
	private final static Logger LOGGER = Logger.getLogger(UserManager.class
			.getName());

	/**
	 * Constructor
	 * 
	 * @throws SecurityException
	 * @throws IOException
	 */
	public UserManager() throws SecurityException, IOException {
            // temporarily disabled - there were some problems with running this
		//LOGGER.addHandler(new FileHandler("errors.log"));
	}

	/**
	 * Adds user object to the database
	 * 
	 * @param user
	 * @return added user
	 */
	public User addUser(User user) {

		em.persist(user);

		em.flush();

		return user;
	}

	/**
	 * Adds user to the database
	 * 
	 * @param login
	 * @param password
	 * @param firstName
	 * @param surName
	 * @param email
	 * @param registrationDate
	 * @param lastIP
	 * @param lastAccess
	 * @param role
	 * @return added user
	 */
	public User addUser(String login, String password, String firstName,
			String surName, String email, Date registrationDate, String lastIP,
			Date lastAccess, int role) {

		User user = new User();

		user.setLoginC(login).setPasswordC(password).setFirstNameC(firstName)
				.setSurNameC(surName).setEmailC(email)
				.setRegistrationDateC(registrationDate).setLastIPC(lastIP)
				.setLastAccessC(lastAccess).setRoleC(role);

		User addedUser = this.addUser(user);

		return addedUser;
	}

	/**
	 * Returns the user according to the given user id
	 * 
	 * @param id
	 * @return
	 */
	public User getUserByID(long iD) {

		return em.find(User.class, iD);
	}
        
        public User getUserByLogin(String login) {
            // check user validity
            if (login == null) {
                throw new IllegalArgumentException("Cannot fetch user with null login");
            }

            User user = null;

            // criteria builder
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Object> criteriaQuery = cb.createQuery();
            Root<User> from = criteriaQuery.from(User.class);

            CriteriaQuery<Object> select = criteriaQuery.select(from);
            Predicate restrictions = cb.equal(from.get("login"), login);

            select.where(restrictions);

            TypedQuery<Object> typedQuery = em.createQuery(select);
            List resultList = typedQuery.getResultList();

            // List resultList = query.getResultList();
            if (resultList.size() > 0) {
                user = (User) resultList.get(0);
            }
            
            return user;
        }
        public User getUserByEmail(String email){
             if (email == null) {
                throw new IllegalArgumentException("Cannot fetch user with null email");
            }
            User user = null;
            // criteria builder
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Object> criteriaQuery = cb.createQuery();
            Root<User> from = criteriaQuery.from(User.class);

            CriteriaQuery<Object> select = criteriaQuery.select(from);
            Predicate restrictions = cb.equal(from.get("email"), email);

            select.where(restrictions);
            TypedQuery<Object> typedQuery = em.createQuery(select);
            List resultList = typedQuery.getResultList();            
            
            if (resultList.size() > 0) {
                user = (User) resultList.get(0);
            }            
            return user;
        }
        

	/**
	 * Update user
	 * 
	 * @param user
	 * 
	 */
	public void updateUser(User user) {

		User userToUpdate = getUserByID(user.getId());

		userToUpdate.setCardsC(user.getCards()).setEmailC(user.getEmail())
				.setFirstNameC(user.getFirstName())
				.setLastAccessC(user.getLastAccess())
				.setLastIPC(user.getLastIP()).setLoginC(user.getLogin())
				.setPasswordC(user.getPassword())
				.setRegistrationDateC(user.getRegistrationDate())
				.setRoleC(user.getRole()).setSurNameC(user.getSurName());

		em.flush();
	}

	/**
	 * Deletes an user according to the given user id
	 * 
	 * @param id
	 * @return true if the user was deleted, false when it was not
	 */
	public boolean deleteUserById(long iD) {

		User userToDelete = em.find(User.class, iD);
		try {
			em.remove(userToDelete);
		} catch (IllegalArgumentException ex) {
			LOGGER.log(Level.SEVERE, ex.getMessage());
			return false;
		}

		em.flush();

		return true;
	}	

	/**
	 * Add card of user determined by given id
	 * 
	 * @param card
	 * @param userId
	 */
	public void addCard(Card card, long userId) {

		User userToAddCard = this.getUserByID(userId);

		List<Card> cards = userToAddCard.getCards();
		cards.add(card);
		userToAddCard.setCardsC(cards);

		this.updateUser(userToAddCard);
	}

	/**
	 * Delete card of user determined by given id
	 * 
	 * @param card
	 * @param userId
	 */
	public void deleteCard(Card card, long userId) {

		User userToDeleteCard = this.getUserByID(userId);

		List<Card> cards = userToDeleteCard.getCards();
		cards.remove(card);
		
		userToDeleteCard.setCardsC(cards);

		this.updateUser(userToDeleteCard);
	}

	public EntityManager getEm() {
		return em;
	}

	public void setEm(EntityManager em) {
		this.em = em;
	}

	public EntityManagerFactory getEntityManagerFactory() {
		return entityManagerFactory;
	}

	public void setEntityManagerFactory(
			EntityManagerFactory entityManagerFactory) {
		this.entityManagerFactory = entityManagerFactory;
	}
}
