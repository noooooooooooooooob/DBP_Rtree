package org.dfpl.lecture.dataloading;

public class Borrower {
    private String name;
    private String loanNumber;
    public Borrower(String name, String loanNumber) {
        this.name = name;
        this.loanNumber = loanNumber;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getLoanNumber() {
        return loanNumber;
    }
    public void setLoanNumber(String loanNumber) {
        this.loanNumber = loanNumber;
    }
    @Override
    public String toString() {
        return name + " " + loanNumber;
    }
}
