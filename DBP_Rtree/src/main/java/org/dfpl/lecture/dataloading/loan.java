package org.dfpl.lecture.dataloading;

public class loan {
    private String loanNumber;
    private String branchName;
    private double amount;

    public loan(String loanNumber, String branchName, double amount) {
        this.loanNumber = loanNumber;
        this.branchName = branchName;
        this.amount = amount;
    }
    public String getLoanNumber() {
        return loanNumber;
    }
    public void setLoanNumber(String loanNumber) {
        this.loanNumber = loanNumber;
    }
    public String getBranchName() {
        return branchName;
    }
    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }
    public double getAmount() {
        return amount;
    }
    public void setAmount(double amount) {
        this.amount = amount;
    }
    @Override
    public String toString()
    {
        return loanNumber + " " + branchName + " " + amount;
    }
}
