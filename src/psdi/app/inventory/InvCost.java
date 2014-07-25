package psdi.app.inventory;

import psdi.util.MXAccessException;
import psdi.mbo.SqlFormat;
import psdi.mbo.MboSetRemote;
import psdi.util.MXApplicationException;
import psdi.app.item.ItemRemote;
import psdi.mbo.MboRemote;

import java.rmi.RemoteException;
import java.util.Date;

import psdi.util.MXException;
import psdi.mbo.MboSet;
import psdi.mbo.Mbo;

public class InvCost extends Mbo implements InvCostRemote
{
    private double accumulativeReceiptQty;
    public Date exchageDate = null;	///AMB<======

    public InvCost(final MboSet ms) throws MXException, RemoteException {
        super(ms);
        this.accumulativeReceiptQty = 0.0;
    }
    
    @Override
    public void add() throws MXException, RemoteException {
        final MboRemote owner = this.getOwner();
        if (owner == null) {
            return;
        }
        if (owner instanceof InventoryRemote) {
            this.setValue("itemnum", owner.getString("itemnum"), 11L);
            this.setValue("location", owner.getString("location"), 11L);
            this.setValue("itemsetid", owner.getString("itemsetid"), 11L);
            this.setValue("siteid", owner.getString("siteid"), 11L);
            this.setValue("glaccount", owner.getString("glaccount"), 11L);
            this.setValue("controlacc", owner.getString("controlacc"), 11L);
            this.setValue("shrinkageacc", owner.getString("shrinkageacc"), 11L);
            this.setValue("invcostadjacc", owner.getString("invcostadjacc"), 11L);
            if (owner.toBeAdded()) {
                this.setValue("CONDRATE", owner.getInt("condrate"), 11L);
                this.setValue("stdcost", owner.getDouble("stdcost"), 2L);
                this.setValue("avgcost", owner.getDouble("avgcost"), 2L);
                this.setValue("lastcost", owner.getDouble("lastcost"), 2L);
                this.setValue("conditioncode", owner.getString("conditioncode"), 11L);
                if (this.getDouble("condrate") == 0.0) {
                    this.setValue("CONDRATE", 100, 11L);
                }
            }
            this.setFieldFlag("conditioncode", 7L, !((Inventory)owner).isConditionEnabled());
        }
    }
    
    @Override
    public void init() throws MXException {
        super.init();
        try {
            final String[] existingReadOnly = { "stdcost", "conditioncode", "avgcost", "lastcost", "condrate" };
            if (!this.toBeAdded()) {
                this.setFieldFlag(existingReadOnly, 7L, true);
                this.setValue("CONTROLACCOUNT", this.getString("CONTROLACC"), 11L);
                this.setValue("INVCOSTADJACCOUNT", this.getString("INVCOSTADJACC"), 11L);
            }
        }
        catch (Exception ex) {}
    }
    
    @Override
    public void appValidate() throws MXException, RemoteException {
        super.appValidate();
        if (this.toBeAdded() && this.isNull("conditioncode")) {
            final ItemRemote item = (ItemRemote)this.getMboSet("ITEM").getMbo(0);
            if (this.isNull("conditioncode") && item.isConditionEnabled()) {
                final Object[] param = { item.getString("itemnum") };
                throw new MXApplicationException("inventory", "noConditionCode", param);
            }
        }
    }
    
    public void updateAverageCost(final double quantity, final double totalvalue, final double exr) throws MXException, RemoteException {
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
    }

    void increaseAccumulativeReceiptQty(final double currentReceiptQty) throws MXException, RemoteException {
        this.accumulativeReceiptQty += currentReceiptQty;
    }
    
    public void updateLastCost(final double value) throws MXException, RemoteException {
        this.setValue("lastcost", value, 2L);
    }
    
    public MboRemote adjustAverageCost(final double newcost) throws MXException, RemoteException {
        if (this.isNull("newavgcost") || this.getMboValue("avgcost").getDouble() == newcost) {
            final InvCostSet invCostSet = (InvCostSet)this.getThisMboSet();
            invCostSet.increaseErrorCount();
            return null;
        }
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
    
    public MboRemote adjustStandardCost(final double newcost) throws MXException, RemoteException {
        final double dbValue = this.getMboValue("stdcost").getInitialValue().asDouble();
        if (this.isNull("newstdcost") || newcost == dbValue || this.getMboValue("stdcost").getDouble() == newcost) {
            final InvCostSet invCostSet = (InvCostSet)this.getThisMboSet();
            invCostSet.increaseErrorCount();
            return null;
        }
        final double cur_bal = this.getCurrentBalance(null, null);
        double physcnt = 0.0;
        try {
            physcnt = this.getPhysicalCount(null, null);
        }
        catch (Exception ex) {}
        final MboRemote invTran = this.getMboSet("INVTRANS").add(2L);
        invTran.setValue("TRANSTYPE", this.getTranslator().toExternalDefaultValue("ITTYPE", "STDCSTADJ", invTran), 2L);
        invTran.setValue("curbal", cur_bal, 2L);
        invTran.setValue("quantity", cur_bal, 2L);
        invTran.setValue("physcnt", physcnt, 2L);
        final double old_cost = this.getDouble("stdcost");
        invTran.setValue("oldcost", old_cost, 2L);
        invTran.setValue("newcost", newcost, 2L);
        invTran.setValueNull("binnum", 2L);
        invTran.setValueNull("lotnum", 2L);
        final String[] ron = { "binnum", "lotnum" };
        invTran.setFieldFlag(ron, 7L, true);
        invTran.setValue("gldebitacct", this.getString("controlaccount"), 2L);
        invTran.setValue("glcreditacct", this.getString("invcostadjaccount"), 2L);
        invTran.setValue("linecost", (newcost - old_cost) * cur_bal, 2L);
        invTran.setValue("conditionCode", this.getString("conditioncode"), 11L);
        this.setValue("stdcost", newcost, 2L);
        invTran.setValue("memo", this.getString("memo"), 2L);
        return invTran;
    }
    
    MboRemote changeCapitalizedStatus(final boolean capitalized, final String capitalacc, final String memo, final MboRemote inventory) throws MXException, RemoteException {
        final String oldInvControlAcc = this.getString("Controlacc");
        String glDebitAcct = null;
        final double oldCost = this.getDefaultIssueCost();
        final double oldcurbal = this.getCurrentBalance(null, null);
        final double oldphyscnt = this.getPhysicalCount(null, null);
        final String[] readonly = { "avgcost", "lastcost", "stdcost", "shrinkage", "costadjacc", "controlacc" };
        if (capitalized) {
            this.setValue("avgcost", 0, 2L);
            this.setValue("stdcost", 0, 2L);
            this.setValue("lastcost", 0, 2L);
            this.setValue("controlacc", capitalacc, 2L);
            this.setValueNull("shrinkageacc", 2L);
            this.setValueNull("INVCOSTADJACC", 2L);
            this.setFieldFlag(readonly, 7L, true);
            glDebitAcct = capitalacc;
        }
        else {
            this.setFieldFlag(readonly, 7L, false);
            if (inventory != null) {
                this.setValue("shrinkageacc", inventory.getString("shrinkageacc"), 2L);
                this.setValue("invcostadjacc", inventory.getString("invcostadjacc"), 2L);
                this.setValue("controlacc", inventory.getString("controlacc"), 2L);
                glDebitAcct = inventory.getString("controlacc");
            }
        }
        final MboRemote itMbo = this.getMboSet("INVTRANS").add(2L);
        itMbo.setValue("TRANSTYPE", this.getTranslator().toExternalDefaultValue("ITTYPE", "CAPCSTADJ", itMbo), 2L);
        itMbo.setValue("itemnum", this.getString("itemnum"), 2L);
        itMbo.setValue("itemsetid", this.getString("itemsetid"), 2L);
        itMbo.setValue("conditionCode", this.getString("conditioncode"), 2L);
        itMbo.setValue("storeloc", this.getString("location"), 2L);
        itMbo.setValue("curbal", oldcurbal, 2L);
        itMbo.setValue("quantity", oldcurbal, 2L);
        itMbo.setValue("physcnt", oldphyscnt, 2L);
        itMbo.setValue("oldcost", oldCost, 2L);
        itMbo.setValue("newcost", 0, 2L);
        itMbo.setValue("linecost", oldcurbal * oldCost, 2L);
        itMbo.setValue("memo", memo, 2L);
        itMbo.setValue("glcreditacct", oldInvControlAcc, 2L);
        itMbo.setValue("gldebitacct", glDebitAcct, 2L);
        return itMbo;
    }
    
    public double getDefaultIssueCost() throws MXException, RemoteException {
        final MboRemote inv = this.getOwner();
        if (inv != null && inv.isBasedOn("INVENTORY")) {
            final String cost = ((Inventory)inv).getCostType();
            if (cost.equalsIgnoreCase("AVERAGE")) {
                return this.getDouble("AVGCOST");
            }
            if (cost.equalsIgnoreCase("STANDARD")) {
                return this.getDouble("STDCOST");
            }
        }
        return 0.0;
    }
    
    MboSetRemote getInvBalanceSet(final String binnum, final String lotnum) throws MXException, RemoteException {
        final StringBuffer where = new StringBuffer();
        SqlFormat sqf = new SqlFormat(this, "itemnum = :itemnum and location = :location and siteid = :siteid and itemsetid = :itemsetid");
        where.append(sqf.format());
        sqf = null;
        if (binnum != null) {
            if (binnum.equals("")) {
                where.append(" and binnum is null");
            }
            else {
                SqlFormat sqf2 = new SqlFormat(this, " and binnum = :1");
                sqf2.setObject(1, "INVBALANCES", "BINNUM", binnum);
                where.append(sqf2.format());
                sqf2 = null;
            }
        }
        if (lotnum != null) {
            if (lotnum.equals("")) {
                where.append(" and lotnum is null");
            }
            else {
                SqlFormat sqf3 = new SqlFormat(this, " and lotnum = :1");
                sqf3.setObject(1, "INVBALANCES", "LOTNUM", lotnum);
                where.append(sqf3.format());
                sqf3 = null;
            }
        }
        if (this.isNull("conditionCode")) {
            where.append(" and conditioncode is null");
        }
        else {
            SqlFormat sqf4 = new SqlFormat(this, " and conditioncode = :1");
            sqf4.setObject(1, "INVBALANCES", "conditionCode", this.getString("conditioncode"));
            where.append(sqf4.format());
            sqf4 = null;
        }
        final MboSetRemote invBalSet = this.getMboSet("$InvBalance" + this.getString("itemnum") + this.getString("location") + binnum + lotnum + this.getString("itemsetid"), "INVBALANCES", where.toString());
        return invBalSet;
    }
    
    double getCurrentBalance(final String binnum, final String lotnum) throws MXException, RemoteException {
        final MboSetRemote invBalSet = this.getInvBalanceSet(binnum, lotnum);
        if (invBalSet.isEmpty()) {
            return 0.0;
        }
        return invBalSet.sum("curbal");
    }
    
    double getPhysicalCount(final String binnum, final String lotnum) throws MXException, RemoteException {
        final MboSetRemote invBalSet = this.getInvBalanceSet(binnum, lotnum);
        if (invBalSet.isEmpty()) {
            return 0.0;
        }
        return invBalSet.sum("physcnt");
    }
    
    MboRemote getInventory() throws MXException, RemoteException {
        final MboRemote owner = this.getOwner();
        if (owner != null && owner instanceof InventoryRemote) {
            return owner;
        }
        return this.getMboSet("INVENTORY").getMbo(0);
    }
    
    @Override
    public void canDelete() throws MXException, RemoteException {
        if (this.toBeAdded() && this.isNull("conditioncode")) {
            return;
        }
        MboRemote owner = this.getOwner();
        if (owner != null && !owner.getName().equals("INVENTORY")) {
            throw new MXAccessException("access", "notauthorized");
        }
        if (owner == null) {
            owner = this.getMboSet("INVENTORY").getMbo(0);
        }
        if (!owner.getString("newcosttype").equals("LIFO") || !owner.getString("newcosttype").equals("FIFO")) {
            return;
        }
        final MboRemote invbal = ((Inventory)owner).getInvBalancesInTheSet(this.getString("conditioncode"));
        if (invbal != null) {
            throw new MXApplicationException("inventory", "noDeleteInvCostInvBalExists");
        }
        if (owner.toBeDeleted()) {
            return;
        }
        if (this.isNull("conditioncode")) {
            throw new MXApplicationException("inventory", "noDeleteInvCost");
        }
        if (this.getInt("condrate") == 100) {
            throw new MXApplicationException("inventory", "noDeleteInvCostOneHundred");
        }
    }
    
    @Override
    public void modify() throws MXException, RemoteException {
        if (this.getOwner() != null) {
            ((Mbo)this.getOwner()).setModified(true);
        }
    }
}