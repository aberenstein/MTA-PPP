package psdi.app.inventory;

import psdi.mbo.MboRemote;

import java.util.Date;

import psdi.security.UserInfo;
import psdi.server.MXServer;
import psdi.app.currency.CurrencyServiceRemote;

import java.rmi.RemoteException;

import psdi.util.MXException;
import psdi.mbo.MboSet;

public class InvCostCust extends InvCost
{
    protected double accumulativeReceiptQty;
    
    public InvCostCust(final MboSet ms) throws MXException, RemoteException {
        super(ms);
        this.accumulativeReceiptQty = 0.0;
    }
    
    @Override
    public void init() throws MXException {
        super.init();
        try {
            final String[] existingReadOnly = { "avgcost2" };
            if (!this.toBeAdded()) {
                this.setFieldFlag(existingReadOnly, 7L, true);
                if (this.getMboLogger().isDebugEnabled()) {
                    this.getMboLogger().debug("Set avgcost2 READONLY=true");
                }
            }
        }
        catch (Exception ex) {}
    }
    
	///AMB===v===
	/*
    @Override
    public void updateAverageCost(final double quantity, final double totalvalue, final double exr) throws MXException, RemoteException {
        System.out.println("psdi.app.inventory.InvCostCust updateAverageCost");
        final double cur_bal = this.getCurrentBalance(null, null) + this.accumulativeReceiptQty;
        if (cur_bal + quantity <= 0.0 || (totalvalue == 0.0 && quantity == 0.0)) {
            return;
        }
        if (cur_bal > 0.0) {
            final double avgcost = this.getDouble("avgcost");
            this.setValue("avgcost", (avgcost * cur_bal + totalvalue * exr) / (cur_bal + quantity), 2L);
        }
        else {
            this.setValue("avgcost", totalvalue * exr / quantity, 2L);
        }
        if (this.getMboLogger().isDebugEnabled()) {
            this.getMboLogger().debug("Inicio del c\u00e1lculo de invcost.avgcost2");
        }
        final UserInfo user = this.getUserInfo();
        final CurrencyServiceRemote currService = (CurrencyServiceRemote)MXServer.getMXServer().lookup("CURRENCY");
        final Date date = MXServer.getMXServer().getDate(this.getClientLocale(), this.getClientTimeZone());
        final String baseCurrency1 = currService.getBaseCurrency1(this.getString("orgid"), user);
        final String baseCurrency2 = currService.getBaseCurrency2(this.getString("orgid"), user);
        if (this.getMboLogger().isDebugEnabled()) {
            this.getMboLogger().debug("Moneda1 :" + baseCurrency1);
            this.getMboLogger().debug("Moneda2 :" + baseCurrency2);
        }
        if (!baseCurrency2.equals("") && baseCurrency2 != null) {
            if (this.getMboLogger().isDebugEnabled()) {
                this.getMboLogger().debug("Total moneda origen: " + totalvalue);
                this.getMboLogger().debug("Exchange rate base 1: " + exr);
            }
            final double totalValueBase1 = totalvalue * exr;
            this.getMboLogger().debug("Total base 1: " + totalValueBase1);
            final double rate2 = currService.getCurrencyExchangeRate(user, baseCurrency1, baseCurrency2, date, this.getString("orgid"));
            if (this.getMboLogger().isDebugEnabled()) {
                this.getMboLogger().debug("Exchange rate 2: " + rate2);
            }
            final double exr2 = rate2;
            if (cur_bal > 0.0) {
                final double avgcost2 = this.getDouble("avgcost2");
                this.setValue("avgcost2", (avgcost2 * cur_bal + totalValueBase1 * exr2) / (cur_bal + quantity), 2L);
            }
            else {
                this.setValue("avgcost2", totalValueBase1 * exr2 / quantity, 2L);
            }
            if (this.getMboLogger().isDebugEnabled()) {
                this.getMboLogger().debug("Set invcost.avgcost2=" + this.getDouble("avgcost2"));
            }
        }
    }    
	*/
	///AMB===^===

    @Override
	public void updateAverageCost(double quantity, double totalvalue, double exr) throws MXException, RemoteException
	{ 
		///AMB===v===
		double cur_bal = getCurrentBalance(null, null) + this.accumulativeReceiptQty;
		if ((cur_bal + quantity <= 0.0D) || ((totalvalue == 0.0D) && (quantity == 0.0D))) {
			return;
		}

		double new_avgcost;
		if (cur_bal > 0.0D)
		{
			double avgcost = getDouble("avgcost");
			new_avgcost = (avgcost * cur_bal + totalvalue * exr) / (cur_bal + quantity);
		} else {
			new_avgcost = totalvalue * exr / quantity;
		}
		setValue("avgcost", new_avgcost, 2L);
    
		UserInfo user = getUserInfo();
    
		CurrencyServiceRemote currService = (CurrencyServiceRemote)MXServer.getMXServer().lookup("CURRENCY");
    
		Date date = MXServer.getMXServer().getDate(getClientLocale(), getClientTimeZone());
		if (super.exchageDate != null) {
			date = super.exchageDate;
			super.exchageDate = null;
		}
    
		String baseCurrency1 = currService.getBaseCurrency1(getString("orgid"), user);
		String baseCurrency2 = currService.getBaseCurrency2(getString("orgid"), user);
    
		if ((!baseCurrency2.equals("")) && (baseCurrency2 != null))
		{
			double exr2 = currService.getCurrencyExchangeRate(user, baseCurrency1, baseCurrency2, date, getString("orgid"));
      
			if (cur_bal + quantity != 0)
			{
				double avgcost2 = getDouble("avgcost2");
				double new_avgcost2 = exr != 1? ((avgcost2 * cur_bal + totalvalue) / (cur_bal + quantity)):			// la OC esta en la moneda 2 (USD)
					                            ((avgcost2 * cur_bal + totalvalue * exr2) / (cur_bal + quantity));	// la OC esta en la moneda 1 (ARS)			
				setValue("avgcost2", new_avgcost2, 2L);
			}
		}

		///AMB===^===
	} 
    
	@Override
    void increaseAccumulativeReceiptQty(final double currentReceiptQty) throws MXException, RemoteException {
        this.accumulativeReceiptQty += currentReceiptQty;
        if (this.getMboLogger().isDebugEnabled()) {
            this.getMboLogger().debug("increaseAccumulativeReceiptQty (local)");
        }
    }
    
    @Override
    public void add() throws MXException, RemoteException {
        super.add();
        final MboRemote owner = this.getOwner();
        if (owner == null) {
            return;
        }
        if (owner instanceof InventoryRemote && owner.toBeAdded()) {
            this.setValue("avgcost2", owner.getDouble("avgcost2"), 2L);
            if (this.getMboLogger().isDebugEnabled()) {
                this.getMboLogger().debug("toBeAdded - Set invcost.avgcost2=" + this.getDouble("avgcost2"));
            }
        }
    }
    
    public MboRemote adjustAverageCost(final double newcost, final double newcost2) throws MXException, RemoteException {
        if ((this.isNull("newavgcost") || this.getMboValue("avgcost").getDouble() == newcost) && (this.isNull("newavgcost2") || this.getMboValue("avgcost2").getDouble() == newcost2)) {
            final InvCostSet invCostSet = (InvCostSet)this.getThisMboSet();
            invCostSet.increaseErrorCount();
            return null;
        }
        final MboRemote invTran = this.adjustAverageCost(newcost);
        final double old_cost2 = this.getDouble("avgcost2");
        invTran.setValue("oldcost2", old_cost2, 2L);
        invTran.setValue("newcost2", newcost2, 2L);
        this.setValue("avgcost2", newcost2, 2L);
        if (this.getMboLogger().isDebugEnabled()) {
            this.getMboLogger().debug("adjustAverageCost - Set invcost.avgcost2=" + this.getDouble("avgcost2"));
        }
        return invTran;
    }
    
    @Override
    public MboRemote adjustAverageCost(final double newcost) throws MXException, RemoteException {
        final double cur_bal = this.getCurrentBalance(null, null);
        double physcnt = 0.0;
        try {
            physcnt = this.getPhysicalCount(null, null);
        }
        catch (Exception ex) {}
        final MboRemote invTran = this.getMboSet("INVTRANS").add(2L);
        invTran.setValue("TRANSTYPE", this.getTranslator().toExternalDefaultValue("ITTYPE", "AVGCSTADJ", invTran), 2L);
        invTran.setValue("curbal", cur_bal, 2L);
        invTran.setValue("physcnt", physcnt, 2L);
        invTran.setValue("quantity", cur_bal, 2L);
        final double old_cost = this.getDouble("avgcost");
        invTran.setValue("oldcost", old_cost, 2L);
        invTran.setValue("newcost", newcost, 2L);
        invTran.setValueNull("binnum", 2L);
        invTran.setValueNull("lotnum", 2L);
        final String[] ron = { "binnum", "lotnum" };
        invTran.setFieldFlag(ron, 7L, true);
        invTran.setValue("gldebitacct", this.getString("controlaccount"), 2L);
        invTran.setValue("glcreditacct", this.getString("invcostadjaccount"), 2L);
        invTran.setValue("linecost", (newcost - old_cost) * cur_bal, 2L);
        invTran.setValue("conditioncode", this.getString("conditioncode"), 11L);
        invTran.setValue("memo", this.getString("memo"), 2L);
        this.setValue("avgcost", newcost, 2L);
        return invTran;
    }
}