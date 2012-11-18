package cz.muni.fi.pa165.cards.db;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Index;

@Entity
@Embeddable
public class User implements Serializable {

	private static final long serialVersionUID = 333L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "user_id")
        @Field
	private Long Id;

        @Field
	private String login;

      
	private String password;

       
	private String firstName;

        
	private String surName;

        
	private String email;

    
	private Date registrationDate;

	private String lastIP;

   
	private Date lastAccess;

	private int role;

        private String authority;
        
	// collections have fetch type LAZY by default, cascade type determines
	// whether to access the database
	// and either update or fetch this field when the User is persisted to the
	// database or retrieved
	@OneToMany(cascade = CascadeType.ALL)
	private List<Card> cards = new ArrayList<Card>();

	public void addCard(Card card) {

		cards.add(card);
	}

	public void removeCard(Card card) {

		cards.remove(card);
	}

	public Long getId() {
		return Id;
	}

    public void setId(Long Id) {
        this.Id = Id;
    }

    public void setCards(List<Card> cards) {
        this.cards = cards;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastAccess(Date lastAccess) {
        this.lastAccess = lastAccess;
    }

    public void setLastIP(String lastIP) {
        this.lastIP = lastIP;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setRegistrationDate(Date registrationDate) {
        this.registrationDate = registrationDate;
    }

    public void setRole(int role) {
        this.role = role;
    }

    public void setSurName(String surName) {
        this.surName = surName;
    }

        
        
        
        /**
         * C - continual setter, allows use of  user.setX().set(Y).set(Z) 
         */
	public User setIdC(Long iD) {
		Id = iD;

		return this;
	}

	public String getLogin() {
		return login;
	}

	public User setLoginC(String login) {
		this.login = login;

		return this;
	}

	public String getPassword() {
		return password;
	}

	public User setPasswordC(String password) {
		this.password = password;

		return this;
	}

	public String getFirstName() {
		return firstName;
	}

	public User setFirstNameC(String firstName) {
		this.firstName = firstName;

		return this;
	}

	public String getSurName() {
		return surName;
	}

	public User setSurNameC(String surName) {
		this.surName = surName;

		return this;
	}

	public String getEmail() {
		return email;
	}

	public User setEmailC(String email) {
		this.email = email;

		return this;
	}

	public Date getRegistrationDate() {
		return registrationDate;
	}

	public User setRegistrationDateC(Date registrationDate) {
		this.registrationDate = registrationDate;

		return this;
	}

	public String getLastIP() {
		return lastIP;
	}

	public User setLastIPC(String lastIP) {
		this.lastIP = lastIP;

		return this;
	}

	public Date getLastAccess() {
		return lastAccess;
	}

	public User setLastAccessC(Date lastAccess) {
		this.lastAccess = lastAccess;

		return this;
	}

	public int getRole() {
		return role;
	}

	public User setRoleC(int role) {
		this.role = role;

		return this;
	}

	public List<Card> getCards() {
		return cards;
	}

	public User setCardsC(List<Card> cards) {
		this.cards = cards;

		return this;
	}

    public String getAuthority() {
        return authority;
    }

    public void setAuthority(String authority) {
        this.authority = authority;
    }

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((email == null) ? 0 : email.hashCode());
		result = prime * result
				+ ((firstName == null) ? 0 : firstName.hashCode());
		result = prime * result + ((login == null) ? 0 : login.hashCode());
		result = prime * result
				+ ((password == null) ? 0 : password.hashCode());
		result = prime
				* result
				+ ((registrationDate == null) ? 0 : registrationDate.hashCode());
		result = prime * result + role;
		result = prime * result + ((surName == null) ? 0 : surName.hashCode());
		return result;
	}

	@Override
	public String toString() {
		return "User [login=" + login + ", password=" + password
				+ ", firstName=" + firstName + ", surName=" + surName
				+ ", email=" + email + ", registrationDate=" + registrationDate
				+ ", role=" + role + "]";
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof User))
			return false;
		User other = (User) obj;
		if (email == null) {
			if (other.email != null)
				return false;
		} else if (!email.equals(other.email))
			return false;
		if (firstName == null) {
			if (other.firstName != null)
				return false;
		} else if (!firstName.equals(other.firstName))
			return false;
		if (login == null) {
			if (other.login != null)
				return false;
		} else if (!login.equals(other.login))
			return false;
		if (password == null) {
			if (other.password != null)
				return false;
		} else if (!password.equals(other.password))
			return false;
		if (registrationDate == null) {
			if (other.registrationDate != null)
				return false;
		} else if (!registrationDate.equals(other.registrationDate))
			return false;
		if (role != other.role)
			return false;
		if (surName == null) {
			if (other.surName != null)
				return false;
		} else if (!surName.equals(other.surName))
			return false;
		return true;
	}

}
