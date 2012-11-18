package cz.muni.fi.pa165.cards.managers;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import cz.muni.fi.pa165.cards.db.Card;
import cz.muni.fi.pa165.cards.db.Category;
import cz.muni.fi.pa165.cards.db.User;
import cz.muni.fi.pa165.cards.fulltext.FulltextFilterEntity;
import cz.muni.fi.pa165.cards.fulltext.FulltextSearchHelper;
import cz.muni.fi.pa165.cards.fulltext.FulltextSearchOptions;
import cz.muni.fi.pa165.cards.fulltext.FulltextSearchTemplate;

/**
 * 
 * @author Miro
 */
@Repository
@Transactional
public class CardManager {

	@PersistenceContext
	private EntityManager em;

	public EntityManager getEm() {
		return em;
	}

	public void setEm(EntityManager em) {
		this.em = em;
	}

	public void deleteCard(Card card) {
		em.remove(card);
	}

	public void addCard(Card card) {
		em.persist(card);
		em.flush();
	}

	@Autowired
	private FulltextSearchHelper ftsh;
	private FulltextSearchTemplate ftst;

	public void setFtsh(FulltextSearchHelper ftsh) {
		this.ftsh = ftsh;
	}

	public void setFtst(FulltextSearchTemplate ftst) {
		this.ftst = ftst;
	}

	public void deleteAllCategories(Long cardId) {
		Card cardToEdit = this.getCardById(cardId);
		cardToEdit.setCategories(null);
		this.updateCard(cardToEdit);
	}

	public void addCategories(Set<Category> categories, Card cardToEdit) {
		cardToEdit.setCategories(categories);
		this.updateCard(cardToEdit);
	}

	public void addCategories(Set<Category> categories, Long cardId) {
		Card cardToEdit = this.getCardById(cardId);
		cardToEdit.setCategories(categories);
		this.updateCard(cardToEdit);
	}

	public void addCategory(Category cat, Card card) {
		card.addToCategory(cat);
		this.updateCard(card);
	}

	public void addCategory(Category cat, Long cardId) {
		Card cardToEdit = this.getCardById(cardId);
		cardToEdit.addToCategory(cat);
		this.updateCard(cardToEdit);
	}

	public void deleteCategory(Category cat, Long cardId) {
		Card cardToEdit = this.getCardById(cardId);
		cardToEdit.removeFromCategory(cat);
		this.updateCard(cardToEdit);
	}

	public void deleteCategory(Category cat, Card card) {
		card.removeFromCategory(cat);
		this.updateCard(card);
	}

	public Card getCardById(Long id) {
		return em.find(Card.class, id);
	}

	public Card getCard(Card card) {
		// return em.find(Card.class, id);
		return em.find(Card.class, card.getId());
	}

	public List<?> getPublicCards() {

		List<?> publicCards = em.createQuery(
				"from Card c where c.publicVisibility = true").getResultList();

		return publicCards;
	}

	public List<?> getUserCards(User owner) {

		return em.createQuery("select c from Card c WHERE c.owner=:owner").setParameter("owner", owner).getResultList();
	}

	public void updateCard(Card card) {
		em.merge(card);
		// em.flush();
	}

	/**
	 * Rebuild fulltext index
	 */
	public void rebuildIndex() {
		this.ftsh.rebuildIndex();
	}

	/**
	 * Performs fulltext lucene search on cards and its text values
	 * 
	 * @param fulltextQuery
	 * @param attributesToSearch
	 *            attributes in which search will be performed. Choose from:
	 *            firstName, secondName, company, email, address, telephone,
	 *            description
	 * @param for fulltext search
	 * @return
	 */
	public List<Card> fulltextSearch(final String fulltextQuery,
			Set<String> attributesToSearch) {
		return this.fulltextSearch(fulltextQuery, attributesToSearch, null);
	}

	/**
	 * Performs fulltext lucene search on cards and its text values
	 * 
	 * @param fulltextQuery
	 * @param attributesToSearch
	 *            attributes in which search will be performed. Choose from:
	 *            firstName, secondName, company, email, address, telephone,
	 *            description
	 * @return
	 */
	public List<Card> fulltextSearch(final String fulltextQuery,
			Set<String> attributesToSearch, FulltextSearchOptions options) {
		if (this.ftst == null) {
			// create instance on first fulltext search
			this.ftst = new FulltextSearchTemplate(em);
			this.ftst.setSearchEntity(Card.class);
			// this.ftst.setSearchFields();
		}
		if (attributesToSearch != null && !attributesToSearch.isEmpty()) {
			this.ftst
					.setSearchFields(attributesToSearch.toArray(new String[0]));
		} else {
			return new LinkedList<Card>();
		}

		// process additional options
		if (options != null) {
			List<FulltextFilterEntity> filters = new LinkedList<FulltextFilterEntity>();

			// only for particular user
			if (options.getUserToRestrictOn() != null) {
				filters.add(new FulltextFilterEntity("owner", true,
						"ownedBy_Id", options.getUserToRestrictOn().getId()));
			}

			// only public
			if (options.getOnlyPublic() != null) {
				filters.add(new FulltextFilterEntity("card", true,
						"publicVisibility", options.getOnlyPublic()));
			}

			// set query filters if not empty
			if (filters.isEmpty() == false) {
				this.ftst.setQueryFilters(filters);
			}
		}

		return this.ftst.fulltextSearch(fulltextQuery);
	}

	/**
	 * fulltext lucene search on cards and all its text values
	 * 
	 * @param fulltextQuery
	 * @return
	 */
	public List<Card> fulltextSearch(final String fulltextQuery) {
		Set<String> searchIn = addAllAttributes();
		return this.fulltextSearch(fulltextQuery, searchIn);
	}
	
	private Set<String> addAllAttributes() {
		
		Set<String> searchIn = new HashSet<String>();
		searchIn.add("firstName");
		searchIn.add("secondName");
		searchIn.add("company");
		searchIn.add("email");
		searchIn.add("address");
		searchIn.add("telephone");
		searchIn.add("description");
		
		return searchIn;
	}
	
	public List<Card> fulltextSearchInUserCards( final String fulltextQuery, User user ) {
	
		Set<String> searchIn = addAllAttributes();
		FulltextSearchOptions options =  new FulltextSearchOptions();
		options.setUserToRestrictOn(user);
		
		return fulltextSearch(fulltextQuery, searchIn, options);
	}
	
	public List<Card> fulltextSearchInPublicCards( final String fulltextQuery ) {
		
		Set<String> searchIn = addAllAttributes();
		FulltextSearchOptions options =  new FulltextSearchOptions();
		options.setOnlyPublic(true);
		
		return fulltextSearch(fulltextQuery, searchIn, options);
	}
	
}
