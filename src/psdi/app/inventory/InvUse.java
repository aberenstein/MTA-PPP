package psdi.app.inventory;

import psdi.app.site.SiteRemote;
import psdi.app.site.SiteServiceRemote;

import java.util.Set;
import java.util.Map;

import psdi.app.financial.FinancialServiceRemote;
import psdi.mbo.SqlFormat;
import psdi.app.inventory.virtual.SplitUseLineSet;

import java.util.Iterator;

import psdi.server.MXServerRemote;
import psdi.util.MXApplicationYesNoCancelException;
import psdi.util.MXApplicationException;
import psdi.util.MXMath;
import psdi.app.item.ItemRemote;
import psdi.app.item.Item;

import java.util.Date;

import psdi.app.currency.CurrencyServiceRemote;
import psdi.server.MXServer;
import psdi.mbo.StatusHandler;
import psdi.mbo.MboSetRemote;

import java.rmi.RemoteException;

import psdi.util.MXException;
import psdi.mbo.MboSet;
import psdi.mbo.MboRemote;

import java.util.HashMap;
import java.util.ArrayList;

import psdi.mbo.StatefulMbo;

public class InvUse extends StatefulMbo implements InvUseRemote
{
    ArrayList<InvUseLineRemote> invUseLineList;
    ArrayList<String> usedRotAssetList;
    HashMap<Long, String> usedRotAssetNSMap;
    HashMap<Long, String> usedRotAssetSMap;
    ArrayList<Long> deletedInvUseLineList;
    HashMap<Long, Double> qtyMap;
    HashMap<Long, MboRemote> shipmentLineMap;
    HashMap<Long, Double> rotQtyMap;
    HashMap<Long, MboRemote> invUseLineMap;
    HashMap<Long, Double> invBalQtyNSMap;
    private boolean isListSelected;
    int evaluateSplit;
    double rotatingqty;
    
    public InvUse(final MboSet ms) throws MXException, RemoteException {
        super(ms);
        this.invUseLineList = new ArrayList<InvUseLineRemote>();
        this.usedRotAssetList = new ArrayList<String>();
        this.usedRotAssetNSMap = new HashMap<Long, String>();
        this.usedRotAssetSMap = new HashMap<Long, String>();
        this.deletedInvUseLineList = new ArrayList<Long>();
        this.qtyMap = new HashMap<Long, Double>();
        this.shipmentLineMap = new HashMap<Long, MboRemote>();
        this.rotQtyMap = new HashMap<Long, Double>();
        this.invUseLineMap = new HashMap<Long, MboRemote>();
        this.invBalQtyNSMap = new HashMap<Long, Double>();
        this.isListSelected = false;
        this.evaluateSplit = 0;
        this.rotatingqty = 0.0;
        cust.component.Logger.Log("InvUse");
    }
    
    public String getProcess() {
        return "INVUSE";
    }
    
    @Override
    protected MboSetRemote getStatusHistory() throws MXException, RemoteException {
        return this.getMboSet("INVUSESTATUS");
    }
    
    @Override
    protected StatusHandler getStatusHandler() {
        return new InvUseStatusHandler(this);
    }
    
    @Override
    public String getStatusListName() {
        return "INVUSESTATUS";
    }
    
    @Override
    public void init() throws MXException {
        super.init();
        this.setFieldFlag("siteid", 7L, true);
        this.setFieldFlag("status", 7L, true);
        this.setFieldFlag("autocreated", 7L, true);
        this.setFieldFlag("receipts", 7L, true);
        this.setFieldFlag("shipmentdate", 7L, true);
        this.setFieldFlag("shiptoattn", 7L, true);
    }
    
    @Override
    public void initFieldFlagsOnMbo(final String attrName) throws MXException {
        try {
            final String[] alwaysReadOnly = { "usetype", "fromstoreloc", "invowner" };
            if (!this.toBeAdded()) {
                if (attrName.equalsIgnoreCase("usetype") || attrName.equalsIgnoreCase("fromstoreloc") || attrName.equalsIgnoreCase("invowner")) {
                    if (this.getStatus().equalsIgnoreCase("ENTERED")) {
                        if (this.getMboSet("INVUSELINE").count(16) == 0) {
                            this.setFieldFlag(alwaysReadOnly, 7L, false);
                        }
                        else {
                            this.setFieldFlag(alwaysReadOnly, 7L, true);
                        }
                    }
                    else {
                        this.setFieldFlag(alwaysReadOnly, 7L, true);
                    }
                }
                if (attrName.equalsIgnoreCase("description") && (this.getStatus().equalsIgnoreCase("COMPLETE") || this.getStatus().equalsIgnoreCase("CANCELLED"))) {
                    this.setFieldFlag("description", 7L, true);
                }
                if (!this.isEntered() && !this.getMboSet("INVUSELINE").isEmpty()) {
                    this.getMboSet("INVUSELINE").setFlag(7L, true);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void add() throws MXException, RemoteException {
        super.add();
        final Date currentDT = MXServer.getMXServer().getDate();
        this.setValue("status", this.getTranslator().toExternalDefaultValue("INVUSESTATUS", "ENTERED", this), 2L);
        this.setValue("statusdate", currentDT, 2L);
        this.setValue("changedate", currentDT, 2L);
        this.setValue("changeby", this.getUserInfo().getUserName(), 11L);
        if (this.getProfile().getDefaultSite().equalsIgnoreCase(this.getProfile().getDefaultStoreroomSite())) {
            this.setValue("fromstoreloc", this.getProfile().getDefaultStoreroom(), 2L);
        }
        this.setValue("usetype", this.getTranslator().toExternalDefaultValue("INVUSETYPE", "MIXED", this), 11L);
        final MboRemote location = this.getMboSet("LOCATIONS").getMbo(0);
        if (location != null) {
            this.setValue("invowner", location.getString("invowner"), 2L);
        }
        this.setValue("receipts", "!NONE!", 11L);
        final CurrencyServiceRemote currService = (CurrencyServiceRemote)MXServer.getMXServer().lookup("CURRENCY");
        this.setValue("currencycode", currService.getBaseCurrency1(this.getString("orgid"), this.getUserInfo()), 11L);
        this.setValue("exchangerate", 1.0, 11L);
        this.setValue("exchangerate2", 1.0, 11L);
        this.setValue("exchangedate", MXServer.getMXServer().getDate(), 11L);
    }
    
    @Override
    public String getStatus() throws MXException, RemoteException {
        final String curStatus = this.getTranslator().toInternalString("INVUSESTATUS", this.getString("status"));
        return curStatus;
    }
    
    @Override
    public InvUseLineRemote addInvUseLine(final MboRemote owner) throws MXException, RemoteException {
        if (owner != null) {
            final InvUseLineRemote newMbo = (InvUseLineRemote)this.getMboSet("INVUSELINE").addAtEnd();
            ((InvUseLine)newMbo).setValue("validated", true, 2L);
            newMbo.setPropagateKeyFlag(false);
            newMbo.setValue("siteid", this.getString("siteid"), 2L);
            newMbo.setPropagateKeyFlag(true);
            newMbo.setValue("usetype", this.getString("usetype"), 2L);
            newMbo.setValue("tositeid", owner.getString("siteid"), 2L);
            newMbo.setValue("fromstoreloc", this.getString("fromstoreloc"), 2L);
            newMbo.setValue("itemnum", owner.getString("itemnum"), 2L);
            newMbo.setValue("itemsetid", owner.getString("itemsetid"), 2L);
            newMbo.setValue("refwo", owner.getString("wonum"), 2L);
            newMbo.setValue("ponum", owner.getString("ponum"), 3L);
            newMbo.setValue("polinenum", owner.getString("polinenum"), 2L);
            newMbo.setValue("mrnum", owner.getString("mrnum"), 3L);
            newMbo.setValue("mrlinenum", owner.getString("mrlinenum"), 2L);
            newMbo.setValue("assetnum", owner.getString("assetnum"), 2L);
            String condCode = owner.getString("conditioncode");
            final MboRemote itemMbo = newMbo.getMboSet("ITEM").getMbo(0);
            if (((Item)itemMbo).isConditionEnabled() && condCode.equals("")) {
                final MboRemote itemCond = ((ItemRemote)itemMbo).getOneHundredPercent();
                if (itemCond != null) {
                    condCode = itemCond.getString("conditioncode");
                }
            }
            newMbo.setValue("fromconditioncode", condCode, 2L);
            if (owner.isBasedOn("INVRESERVE")) {
                final MboRemote wo = owner.getMboSet("WORKORDER").getMbo(0);
                if (wo != null) {
                    newMbo.setValue("enteredastask", wo.getString("istask"), 2L);
                }
                newMbo.setValue("requestnum", owner.getString("requestnum"), 11L);
                newMbo.setValue("location", owner.getString("oplocation"), 2L);
                newMbo.setValue("gldebitacct", owner.getString("glaccount"), 11L);
                newMbo.setValue("linetype", this.getTranslator().toExternalDefaultValue("LINETYPE", "ITEM", this), 2L);
                newMbo.setValue("quantity", MXMath.abs(owner.getDouble("reservedqty")), 2L);
                final String issuetolabor = owner.getString("issueto");
                if (issuetolabor != null && !issuetolabor.equals("")) {
                    final MboRemote labor = owner.getMboSet("$LABOR", "LABOR", "laborcode=:issueto and orgid=:orgid").getMbo(0);
                    if (labor != null && !labor.isNull("personid")) {
                        newMbo.setValue("issueto", labor.getString("personid"), 2L);
                    }
                }
                newMbo.setValue("porevisionnum", owner.getString("porevisionnum"), 2L);
                if (owner.getString("description").length() > 50) {
                    newMbo.setValue("remark", owner.getString("description").substring(0, 49), 2L);
                }
                else {
                    newMbo.setValue("remark", owner.getString("description"), 2L);
                }
                if (!newMbo.isTransfer()) {
                    ((InvUseLine)newMbo).updateGlAccounts();
                }
                else {
                    newMbo.setValue("tolot", ((InvUseLine)newMbo).getDefaultLotNum(), 2L);
                }
            }
            return newMbo;
        }
        return null;
    }
    
    @Override
    public void changeStatus(final String status, final Date date, final String memo, final long accessModifier) throws MXException, RemoteException {
        final String desiredMaxStatus = this.getTranslator().toInternalString("INVUSESTATUS", status);
        if (desiredMaxStatus.equalsIgnoreCase("SHIPPED") && this.getMboSet("SHIPMENT").isEmpty()) {
            this.setShipmentLines();
        }
        if (!desiredMaxStatus.equals("CANCELLED")) {
            this.validateStatus(desiredMaxStatus, date, memo, this.getString("binflag"), this.getString("stagingbin"), this.isListSelected());
        }
        try {
            super.changeStatus(status, date, memo, accessModifier);
        }
        catch (Throwable thrownObject) {
            MXException caughtException = null;
            final String invUseNum = this.getString("invusenum");
            final Object[] params = { invUseNum, status };
            caughtException = new MXApplicationException("inventory", "StatusChangeFailure", params, thrownObject);
            throw caughtException;
        }
    }
    
    @Override
    public void changeStatus(final String status, final Date date, final String memo, final String binflag, final String stagingbin, final boolean listtab, final long accessModifier) throws MXException, RemoteException {
        final String desiredMaxStatus = this.getTranslator().toInternalString("INVUSESTATUS", status);
        if (!desiredMaxStatus.equals("CANCELLED")) {
            this.validateStatus(desiredMaxStatus, date, memo, binflag, stagingbin, listtab);
        }
        try {
            super.changeStatus(status, date, memo, accessModifier);
        }
        catch (Throwable thrownObject) {
            MXException caughtException = null;
            final String invUseNum = this.getString("invusenum");
            final Object[] params = { invUseNum, status };
            caughtException = new MXApplicationException("inventory", "StatusChangeFailure", params, thrownObject);
            throw caughtException;
        }
    }
    
    public void validateStatus(final String status, final Date date, final String memo, final String binflag, final String stagingbin, final boolean listtab) throws MXException, RemoteException {
        if (!this.validateLineReservationandSetBin(listtab, binflag, stagingbin, status) && (listtab || this.getMXTransaction().getBoolean("INTEGRATION"))) {
            final Object[] param = { this.getString("invusenum") };
            throw new MXApplicationException("inventory", "statusNotChanged", param);
        }
    }
    
    public void checkReservationandSetBin(final String binflag, final String stagebin, final String status) throws MXException, RemoteException {
        final MboSetRemote invUseLineSet = this.getMboSet("INVUSELINE");
        InvUseLine invUseLine = null;
        int i = 0;
        this.invUseLineList.clear();
        while ((invUseLine = (InvUseLine)invUseLineSet.getMbo(i)) != null) {
            if (!invUseLine.toBeDeleted() && !invUseLine.getString("requestnum").equals("")) {
                final MboRemote invReserve = invUseLine.getSharedInvReserveSet().getMbo(0);
                if (invReserve == null) {
                    this.invUseLineList.add(invUseLine);
                }
            }
            if (status.equals("STAGED")) {
                invUseLine.setStagingBin(binflag, stagebin);
            }
            ++i;
        }
    }
    
    public boolean validateLineReservationandSetBin(final boolean listtab, final String binflag, final String stagebin, final String status) throws MXException, RemoteException {
        this.checkReservationandSetBin(binflag, stagebin, status);
        if (!listtab) {
            if (!this.invUseLineList.isEmpty()) {
                final String invUseLineNumList = this.getInvUseLineNum();
                final Object[] params = { invUseLineNumList };
                if (!this.getUserInfo().isInteractive()) {
                    return false;
                }
                final int userInput = MXApplicationYesNoCancelException.getUserInput("noReservationExistId", MXServer.getMXServer(), this.getUserInfo());
                switch (userInput) {
                    case -1: {
                        throw new MXApplicationYesNoCancelException("noReservationExistId", "inventory", "noReservationExist", params);
                    }
                    case 8: {
                        this.invUseLineList.clear();
                        return true;
                    }
                    case 16: {
                        return this.processInvUseLines();
                    }
                    case 4: {
                        return false;
                    }
                }
            }
        }
        else if (this.invUseLineList.size() > 0) {
            return false;
        }
        return true;
    }
    
    public boolean processInvUseLines() throws MXException, RemoteException {
        if (!this.allReservationDeletedInSet()) {
            this.deleteInvUseLines();
        }
        else {
            this.cancelInvUseLines();
        }
        return true;
    }
    
    public void deleteInvUseLines() throws MXException, RemoteException {
        for (final Object invUseLine_ : this.invUseLineList) {
        	InvUseLine invUseLine = (InvUseLine) invUseLine_;
            invUseLine.delete(2L);
        }
    }
    
    public void cancelInvUseLines() throws MXException, RemoteException {
        for (final Object invUseLine_ : this.invUseLineList) {
        	InvUseLine invUseLine = (InvUseLine) invUseLine_;
            if (this.isStaged()) {
                invUseLine.addMatRecTransRecordForCancelStageTransfer();
            }
            else {
                if (!this.isShipped()) {
                    continue;
                }
                invUseLine.addMatRecTransRecordForCancelShipTransfer();
            }
        }
    }
    
    public String getInvUseLineNum() throws MXException, RemoteException {
        final Iterator it = this.invUseLineList.iterator();
        final StringBuffer invuselinenumlist = new StringBuffer();
        while (it.hasNext()) {
            final InvUseLine invUseLine = (InvUseLine) it.next();
            invuselinenumlist.append(invUseLine.getString("invuselinenum")).append(", ");
        }
        return invuselinenumlist.toString();
    }
    
    public boolean allReservationDeletedInSet() throws MXException, RemoteException {
        return this.getMboSet("INVUSELINE").count() == this.invUseLineList.size();
    }
    
    public boolean isEntered() throws MXException, RemoteException {
        return !this.isNull("STATUS") && this.getStatus().equals("ENTERED");
    }
    
    public boolean isStaged() throws MXException, RemoteException {
        return !this.isNull("STATUS") && this.getStatus().equals("STAGED");
    }
    
    public boolean isShipped() throws MXException, RemoteException {
        return !this.isNull("STATUS") && this.getStatus().equals("SHIPPED");
    }
    
    public boolean isCompleted() throws MXException, RemoteException {
        return !this.isNull("STATUS") && this.getStatus().equals("COMPLETE");
    }
    
    public boolean isCancelled() throws MXException, RemoteException {
        return !this.isNull("STATUS") && this.getStatus().equals("CANCELLED");
    }
    
    @Override
    public void copyInvUseLineSetForReturn(final MboSetRemote matUseTransSet) throws RemoteException, MXException {
        final InvUseLineSetRemote newInvUseLineSet = (InvUseLineSetRemote)this.getMboSet("INVUSELINE");
        newInvUseLineSet.copyInvUseLineSet(matUseTransSet);
    }
    
    @Override
    public void copyInvReserveSetForInvUse(final MboSetRemote invReserveSet) throws RemoteException, MXException {
        final InvUseLineSetRemote invUseLineSet = (InvUseLineSetRemote)this.getMboSet("INVUSELINE");
        invUseLineSet.copyInvReserveSet(invReserveSet);
    }
    
    @Override
    public void copySparePartSetForInvUse(final MboSetRemote sparePartSet) throws RemoteException, MXException {
        final InvUseLineSetRemote invUseLineSet = (InvUseLineSetRemote)this.getMboSet("INVUSELINE");
        invUseLineSet.copySparePartSet(sparePartSet);
    }
    
    @Override
    public void copyInvBalancesSetForItems(final MboSetRemote invBalancesSet) throws RemoteException, MXException {
        final InvUseLineSetRemote invUseLineSet = (InvUseLineSetRemote)this.getMboSet("INVUSELINE");
        invUseLineSet.copyInvBalancesSet(invBalancesSet);
    }
    
    @Override
    public boolean isTransfer() throws MXException, RemoteException {
        return !this.isNull("USETYPE") && this.getTranslator().toInternalString("INVUSETYPE", this.getString("usetype")).equalsIgnoreCase("TRANSFER");
    }
    
    @Override
    public boolean isIssue() throws MXException, RemoteException {
        return !this.isNull("USETYPE") && this.getTranslator().toInternalString("INVUSETYPE", this.getString("usetype")).equalsIgnoreCase("ISSUE");
    }
    
    @Override
    public void staged() throws MXException, RemoteException {
        final MboSetRemote invUseLineSet = this.getMboSet("INVUSELINE");
        final MboSetRemote matrecMboSet = this.getMboSet("$emptyMatRecTrans", "MATRECTRANS", "1=2");
        InvUseLine invUseLine = null;
        int i = 0;
        final HashMap<Long, ArrayList<InvUseLineSplitRemote>> splitMap = this.getInvUseLineSplitRecordsMap();
        while ((invUseLine = (InvUseLine)invUseLineSet.getMbo(i)) != null) {
            if (invUseLine.toBeDeleted()) {
                ++i;
            }
            else {
                if (invUseLine.isReturn()) {
                    throw new MXApplicationException("inventory", "CannotChgStReturnExits");
                }
                final long keyId = invUseLine.getLong("invuselineid");
                final ArrayList<InvUseLineSplitRemote> splitLineList = splitMap.get(keyId);
                invUseLine.updateUnitCost();
                String costtype = null;
                final Inventory inv = (Inventory)invUseLine.getMboSet("INVENTORY").getMbo(0);
                if (inv != null) {
                    costtype = inv.getCostType();
                }
                if (costtype != null && (costtype.equals("LIFO") || costtype.equals("FIFO"))) {
                    invUseLine.addTransactionRecordsLIFOFIFO(matrecMboSet, splitLineList, "STAGED");
                }
                else {
                    invUseLine.addRecordForStageTransfer(matrecMboSet, splitLineList);
                }
                invUseLine.updateInvReserveStagedQty();
                ++i;
            }
        }
        this.getMboSet("INVUSELINE").setFlag(7L, true);
    }
    
    @Override
    public void complete(final String currentMaxStatus) throws MXException, RemoteException {
        final MboSetRemote matuseMboSet = this.getMboSet("$emptyMatUseTrans", "MATUSETRANS", "1=2");
        final MboSetRemote matrecMboSet = this.getMboSet("$emptyMatRecTrans", "MATRECTRANS", "1=2");
        MboRemote invUseLine = null;
        int i = 0;
        HashMap<Long, ArrayList<InvUseLineSplitRemote>> splitMap = null;
        final MboSetRemote invUseLineSet = this.getMboSet("INVUSELINE");
        ((InvUseLineSet)invUseLineSet).getPhyscntdateList().clear();
        if (currentMaxStatus.equals("ENTERED")) {
            splitMap = this.getInvUseLineSplitRecordsMap();
        }
        while ((invUseLine = invUseLineSet.getMbo(i)) != null) {
            if (invUseLine.toBeDeleted() || this.deletedInvUseLineList.contains(invUseLine.getLong("invuselineid"))) {
                ++i;
            }
            else {
                if (!((InvUseLine)invUseLine).isReturn()) {
                    ((InvUseLine)invUseLine).updateUnitCost();
                }
                if (currentMaxStatus.equals("ENTERED")) {
                    final long keyId = invUseLine.getLong("invuselineid");
                    final ArrayList<InvUseLineSplitRemote> splitLineList = splitMap.get(keyId);
                    String costtype = null;
                    final Inventory inv = (Inventory)invUseLine.getMboSet("INVENTORY").getMbo(0);
                    if (inv != null && !((InvUseLine)invUseLine).isReturn()) {
                        costtype = inv.getCostType();
                    }
                    if (!((InvUseLine)invUseLine).isReturn() && (costtype.equals("LIFO") || costtype.equals("FIFO"))) {
                        if (((InvUseLine)invUseLine).isTransfer()) {
                            ((InvUseLine)invUseLine).addTransactionRecordsLIFOFIFO(matrecMboSet, splitLineList, "COMPLETE");
                        }
                        else {
                            ((InvUseLine)invUseLine).addTransactionRecordsLIFOFIFO(matuseMboSet, splitLineList, "COMPLETE");
                        }
                    }
                    else if (((InvUseLine)invUseLine).isTransfer()) {
                        ((InvUseLine)invUseLine).addTransferRecordForComplete(matrecMboSet, splitLineList);
                    }
                    else {
                        ((InvUseLine)invUseLine).addIssueReturnRecordForComplete(matuseMboSet, splitLineList);
                    }
                }
                else {
                    if (currentMaxStatus.equals("STAGED")) {
                        ((InvUseLine)invUseLine).updateStagedInvBalances("COMPLETE");
                    }
                    if (((InvUseLine)invUseLine).isTransfer()) {
                        ((InvUseLine)invUseLine).addTransferRecordForComplete(matrecMboSet, null);
                    }
                    else {
                        ((InvUseLine)invUseLine).addIssueReturnRecordForComplete(matuseMboSet, null);
                    }
                }
                ((InvUseLine)invUseLine).updateInvReserveActualQty();
                ++i;
            }
        }
        final ArrayList<String> linelist = ((InvUseLineSet)invUseLineSet).getPhyscntdateList();
        if (!linelist.isEmpty()) {
            ((InvUseLineSet)invUseLineSet).addWarning(new MXApplicationException("inventory", "oldphyscntdate"));
        }
        this.getMboSet("INVUSELINE").setFlag(7L, true);
    }
    
    @Override
    public void shipped(final String currentMaxStatus) throws MXException, RemoteException {
        final MboSetRemote matrecMboSet = this.getMboSet("$emptyMatRecTrans", "MATRECTRANS", "1=2");
        MboRemote invUseLine = null;
        int i = 0;
        HashMap<Long, ArrayList<InvUseLineSplitRemote>> splitMap = null;
        final MboSetRemote invUseLineSet = this.getMboSet("INVUSELINE");
        ((InvUseLineSet)invUseLineSet).getPhyscntdateList().clear();
        this.setValue("exchangerate", this.getExchangeRate(MXServer.getMXServer().getDate()), 11L);
        this.setValue("exchangedate", MXServer.getMXServer().getDate(), 11L);
        this.setValue("exchangerate2", this.getExchangeRate2(MXServer.getMXServer().getDate()), 11L);
        if (currentMaxStatus.equals("ENTERED")) {
            splitMap = this.getInvUseLineSplitRecordsMap();
        }
        while ((invUseLine = invUseLineSet.getMbo(i)) != null) {
            if (invUseLine.toBeDeleted()) {
                ++i;
            }
            else {
                ((InvUseLine)invUseLine).updateUnitCost();
                if (currentMaxStatus.equals("ENTERED")) {
                    final long keyId = invUseLine.getLong("invuselineid");
                    final ArrayList<InvUseLineSplitRemote> splitLineList = splitMap.get(keyId);
                    String costtype = null;
                    final Inventory inv = (Inventory)invUseLine.getMboSet("INVENTORY").getMbo(0);
                    if (inv != null) {
                        costtype = inv.getCostType();
                    }
                    if (costtype != null && (costtype.equals("LIFO") || costtype.equals("FIFO"))) {
                        ((InvUseLine)invUseLine).addTransactionRecordsLIFOFIFO(matrecMboSet, splitLineList, "SHIPPED");
                    }
                    else {
                        ((InvUseLine)invUseLine).addRecordForShipTransfer(matrecMboSet, splitLineList);
                    }
                }
                else {
                    ((InvUseLine)invUseLine).addRecordForShipTransfer(matrecMboSet, null);
                }
                if (this.isStaged()) {
                    ((InvUseLine)invUseLine).updateStagedInvBalances("SHIPPED");
                }
                ((InvUseLine)invUseLine).updateInvReserveShippedQty();
                ++i;
            }
        }
        final ArrayList<String> linelist = ((InvUseLineSet)invUseLineSet).getPhyscntdateList();
        if (!linelist.isEmpty()) {
            ((InvUseLineSet)invUseLineSet).addWarning(new MXApplicationException("inventory", "oldphyscntdate"));
        }
        this.getMboSet("INVUSELINE").setFlag(7L, true);
        final MboRemote shipment = this.getMboSet("SHIPMENT").getMbo(0);
        if (shipment != null) {
            this.setValue("shipmentdate", shipment.getDate("shipdate"), 11L);
            this.setValue("shiptoattn", shipment.getString("shiptoattn"), 11L);
            final MboSetRemote shipmentLineSet = shipment.getMboSet("SHIPMENTLINE");
            i = 0;
            MboRemote shipmentLine = null;
            while ((shipmentLine = shipmentLineSet.getMbo(i)) != null) {
                shipmentLine.setValue("packingslipnum", shipment.getString("packingslipnum"), 2L);
                shipmentLine.setValue("shipmentnum", shipment.getString("shipmentnum"), 2L);
                ++i;
            }
        }
    }
    
    public HashMap<Long, ArrayList<InvUseLineSplitRemote>> getInvUseLineSplitRecordsMap() throws MXException, RemoteException {
        int i = 0;
        int j = 0;
        MboRemote splitInvUseLine = null;
        MboRemote invUseLineSplit = null;
        final HashMap<Long, ArrayList<InvUseLineSplitRemote>> splitMap = new HashMap<Long, ArrayList<InvUseLineSplitRemote>>();
        splitMap.clear();
        this.usedRotAssetNSMap.clear();
        this.invBalQtyNSMap.clear();
        final MboSetRemote splitUseLineSet = this.getMboSet("SPLITUSELINE");
        if (splitUseLineSet.isEmpty() && this.getEvaluateSplitFlag() != 1) {
            final MboSetRemote tempSetRemote = this.getMboSet("INVUSELINE");
            ((SplitUseLineSet)splitUseLineSet).getSplitUseLineSet(tempSetRemote);
        }
        while ((splitInvUseLine = splitUseLineSet.getMbo(i)) != null) {
            MboSetRemote invUseLineSplitSet;
            Long lineidKey;
            ArrayList<InvUseLineSplitRemote> splitList;
            for (invUseLineSplitSet = this.getMboSet("INVUSELINESPLIT"), j = 0; (invUseLineSplit = invUseLineSplitSet.getMbo(j)) != null; ++j) {
                if (invUseLineSplit.getLong("invuselineid") == splitInvUseLine.getLong("invuselineid")) {
                    lineidKey = invUseLineSplit.getLong("invuselineid");
                    if (splitMap.containsKey(lineidKey)) {
                        splitList = splitMap.get(lineidKey);
                        splitList.add((InvUseLineSplitRemote)invUseLineSplit);
                    }
                    else {
                        splitList = new ArrayList<InvUseLineSplitRemote>();
                        splitList.add((InvUseLineSplitRemote)invUseLineSplit);
                        splitMap.put((long)lineidKey, splitList);
                    }
                }
            }
            if (splitInvUseLine.getDouble("quantity") == 0.0) {
                final MboRemote invuseline = splitInvUseLine.getMboSet("INVUSELINE").getMbo(0);
                invuseline.setValue("quantity", 0, 2L);
                splitMap.put(splitInvUseLine.getLong("invuselineid"), null);
            }
            ++i;
        }
        return splitMap;
    }
    
    @Override
    public void cancelled() throws MXException, RemoteException {
        final MboSetRemote invUseLineSet = this.getMboSet("INVUSELINE");
        InvUseLine invUseLine = null;
        for (int i = 0; (invUseLine = (InvUseLine)invUseLineSet.getMbo(i)) != null; ++i) {
            if (!this.isEntered()) {
                if (this.isStaged()) {
                    invUseLine.addMatRecTransRecordForCancelStageTransfer();
                }
                else {
                    if (!invUseLine.getMboSet("MATRECSHIPRECEIPT").isEmpty()) {
                        final Object[] params = { this.getString("invusenum") };
                        throw new MXApplicationException("inventory", "CannotCancelShippedStatus", params);
                    }
                    invUseLine.addMatRecTransRecordForCancelShipTransfer();
                }
            }
            invUseLine.updateInvReserveForCancel();
        }
    }
    
    public void isSplitNeeded(final MboRemote invUseLine) throws MXException, RemoteException {
        if (((InvUseLine)invUseLine).needsSplitting()) {
            final Object[] param = { this.getString("invusenum") };
            throw new MXApplicationException("inventory", "linesRequireSplit", param);
        }
    }
    
    public boolean isListSelected() {
        return this.isListSelected;
    }
    
    public void setListSelected(final boolean isListSelected) {
        this.isListSelected = isListSelected;
    }
    
    @Override
    public void validateLines(final String status) throws MXException, RemoteException {
        try {
            int i = 0;
            MboRemote invuseline = null;
            final String orgID = this.getString("orgid");
            final String siteID = this.getString("siteid");
            String lineOrgID = null;
            String toSiteID = null;
            String requireShipmentFrom = null;
            String requirePOFrom = null;
            final MboSetRemote invuselineset = this.getMboSet("INVUSELINE");
            final long maxInvUseLineLimit = ((InvUseLineSet)invuselineset).readMaxInvUseLineLimitProperty();
            final Object[] params1 = { maxInvUseLineLimit + "" };
            if (invuselineset.count() > maxInvUseLineLimit) {
                throw new MXApplicationException("inventory", "MaxInvUseLineLimitExceeded", params1);
            }
            double rotqty = 0.0;
            final HashMap<Long, Double> rotQtyMap = this.getRotQtyMap();
            for (final Double value : rotQtyMap.values()) {
                rotqty += value;
            }
            if (rotqty > maxInvUseLineLimit) {
                throw new MXApplicationException("inventory", "MaxInvUseLineLimitExceeded", params1);
            }
            final double lines = invuselineset.count(16) + rotqty - rotQtyMap.size();
            if (lines > maxInvUseLineLimit) {
                throw new MXApplicationException("inventory", "MaxInvUseLineLimitExceeded", params1);
            }
            final String maxStatus = this.getTranslator().toInternalString("INVUSESTATUS", status, this);
            if (maxStatus.equalsIgnoreCase("CANCELLED")) {
                return;
            }
            if (maxStatus.equalsIgnoreCase("COMPLETE")) {
                requireShipmentFrom = this.getMboServer().getMaxVar().getString("TXFRREQSHIP", this.getString("orgid"));
            }
            requirePOFrom = this.getMboServer().getMaxVar().getString("TXFRREQPO", this.getString("orgid"));
            while ((invuseline = invuselineset.getMbo(i)) != null) {
                ++i;
                final String toOrgID = invuseline.getString("toorgid");
                if (maxStatus.equalsIgnoreCase("COMPLETE") || maxStatus.equalsIgnoreCase("SHIPPED") || maxStatus.equalsIgnoreCase("STAGED")) {
                    this.validateHardReservation(invuseline);
                    if (((InvUseLine)invuseline).isIssue() && ((InvUseLine)invuseline).isRotating() && (((InvUseLine)invuseline).isNull("location") || ((InvUseLine)invuseline).getString("location").equals(""))) {
                        throw new MXApplicationException("asset", "movelocationnotfound");
                    }
                }
                this.checkNegativeBalanace(invuseline);
                if (this.isListSelected) {
                    this.isSplitNeeded(invuseline);
                    if (maxStatus.equalsIgnoreCase("SHIPPED")) {
                        throw new MXApplicationException("inventory", "cannotBeShipped");
                    }
                }
                if (this.isEntered() && !invuseline.getString("rotassetnum").equals("")) {
                    this.validateRotatingAssets(invuseline);
                }
                if (((InvUseLine)invuseline).getBoolean("validated")) {
                    ((InvUseLine)invuseline).preValidateLine();
                    ((InvUseLine)invuseline).validateLine();
                }
                ((InvUseLine)invuseline).checkAssetWOLocValidate();
                if (maxStatus.equalsIgnoreCase("COMPLETE") && ((InvUseLine)invuseline).isTransfer()) {
                    if (!orgID.equalsIgnoreCase(toOrgID)) {
                        throw new MXApplicationException("inventory", "NoShipmentCreatedForTransfer");
                    }
                    final String requireShipmentTo = this.getMboServer().getMaxVar().getString("TXFRREQSHIP", toOrgID);
                    if ((requireShipmentFrom.equalsIgnoreCase("ORG") || requireShipmentTo.equalsIgnoreCase("ORG")) && !orgID.equalsIgnoreCase(invuseline.getString("toorgid"))) {
                        throw new MXApplicationException("inventory", "NoShipmentCreatedForTransfer");
                    }
                    if (requireShipmentFrom.equalsIgnoreCase("SITE") || requireShipmentTo.equalsIgnoreCase("SITE")) {
                        if (orgID.equalsIgnoreCase(toOrgID) && !siteID.equalsIgnoreCase(invuseline.getString("tositeid"))) {
                            throw new MXApplicationException("inventory", "NoShipmentCreatedForTransfer");
                        }
                        if (!orgID.equalsIgnoreCase(toOrgID)) {
                            throw new MXApplicationException("inventory", "NoShipmentCreatedForTransfer");
                        }
                    }
                    if (requireShipmentFrom.equalsIgnoreCase("ALL") || requireShipmentTo.equalsIgnoreCase("ALL")) {
                        throw new MXApplicationException("inventory", "NoShipmentCreatedForTransfer");
                    }
                }
                if (maxStatus.equalsIgnoreCase("SHIPPED")) {
                    this.validateForShipped(invuseline);
                    if (lineOrgID == null) {
                        toSiteID = invuseline.getString("tositeid");
                        lineOrgID = invuseline.getString("toorgid");
                    }
                    else {
                        if (!lineOrgID.equalsIgnoreCase(toOrgID)) {
                            throw new MXApplicationException("inventory", "TransferToSameSite");
                        }
                        if (lineOrgID.equalsIgnoreCase(toOrgID) && !toSiteID.equalsIgnoreCase(invuseline.getString("tositeid"))) {
                            throw new MXApplicationException("inventory", "TransferToSameSite");
                        }
                    }
                    final String toStoreroom = invuseline.getString("tostoreloc");
                    final String toSite = invuseline.getString("tositeid");
                    if (((InvUseLine)invuseline).isRotating() && ((InvUseLine)invuseline).getSharedInventory(toStoreroom, toSite) == null) {
                        final String destination = toStoreroom + "/" + toSite;
                        final Object[] params2 = { ((InvUseLine)invuseline).getString("itemnum"), destination };
                        throw new MXApplicationException("asset", "noInventory", params2);
                    }
                }
                final String requirePOTo = this.getMboServer().getMaxVar().getString("TXFRREQPO", toOrgID);
                final Object[] params3 = { toOrgID, invuseline.getString("invuselinenum"), status };
                if (!requirePOFrom.equalsIgnoreCase("NEVER")) {
                    if (requirePOTo.equalsIgnoreCase("NEVER")) {
                        continue;
                    }
                    if ((requirePOFrom.equalsIgnoreCase("ORG") || requirePOTo.equalsIgnoreCase("ORG")) && !orgID.equalsIgnoreCase(toOrgID) && invuseline.isNull("ponum")) {
                        throw new MXApplicationException("inventory", "LineRequiresPOReference", params3);
                    }
                    if (!requirePOFrom.equalsIgnoreCase("SITE") && !requirePOTo.equalsIgnoreCase("SITE")) {
                        continue;
                    }
                    if (orgID.equalsIgnoreCase(toOrgID) && !siteID.equalsIgnoreCase(invuseline.getString("tositeid")) && invuseline.isNull("ponum")) {
                        throw new MXApplicationException("inventory", "LineRequiresPOReference", params3);
                    }
                    if (!orgID.equalsIgnoreCase(toOrgID) && !siteID.equalsIgnoreCase(invuseline.getString("tositeid")) && invuseline.isNull("ponum")) {
                        throw new MXApplicationException("inventory", "LineRequiresPOReference", params3);
                    }
                    continue;
                }
            }
        }
        catch (Throwable thrownObject) {
            MXException caughtException = null;
            final String invUseNum = this.getString("invusenum");
            final Object[] param = { invUseNum, status };
            caughtException = new MXApplicationException("inventory", "StatusChangeFailure", param, thrownObject);
            throw caughtException;
        }
    }
    
    public void checkNegativeBalanace(final MboRemote invuseline) throws MXException, RemoteException {
        try {
            ((InvUseLine)invuseline).checkForNegativeAvlBalanceBeforeSplitting();
        }
        catch (MXException mxe) {
            throw mxe;
        }
    }
    
    public void validateRotatingAssets(final MboRemote invUseLine) throws MXException, RemoteException {
        final String sqlWhere = " itemnum=:itemnum and itemsetid=:itemsetid and fromstoreloc=:fromstoreloc and siteid=:siteid  and rotassetnum=:rotassetnum and invusenum in ( select invusenum from invuse  where siteid=invuselinesplit.siteid  and status in ( select value from synonymdomain where domainid = 'INVUSESTATUS' and maxvalue in ('STAGED','SHIPPED'))  and receipts in ( select value from synonymdomain where domainid='RECEIPTS' and maxvalue in ('NONE') ))";
        final SqlFormat sqf = new SqlFormat(invUseLine, sqlWhere);
        final MboSetRemote assetSet = invUseLine.getMboSet("$INVUSELINE" + invUseLine.getLong("invuselineid"), "INVUSELINESPLIT", sqf.format());
        if (!assetSet.isEmpty()) {
            final Object[] params = { invUseLine.getString("invuselinenum") };
            throw new MXApplicationException("inventory", "dupRotatingAsset", params);
        }
    }
    
    public void validateGLAccounts(final MboRemote invuseline) throws MXException, RemoteException {
        if (invuseline.isNull("gldebitacct") || invuseline.isNull("gldebitacct")) {
            throw new MXApplicationException("inventory", "matusetransNullChargeTo");
        }
        final FinancialServiceRemote fsr = (FinancialServiceRemote)MXServer.getMXServer().lookup("FINANCIAL");
        if (fsr.glRequiredForTrans(this.getUserInfo(), this.getString("orgid")) && (invuseline.isNull("gldebitacct") || invuseline.isNull("glcreditacct"))) {
            throw new MXApplicationException("financial", "GLRequiredForTrans");
        }
        final String glDebitAcct = invuseline.getString("gldebitacct");
        final String orgID = invuseline.getString("toorgid");
        if (!glDebitAcct.equals("")) {
            final String[] params = { glDebitAcct };
            if (!fsr.validateFullGLAccount(this.getUserInfo(), glDebitAcct, orgID)) {
                throw new MXApplicationException("inventory", "InvalidGLAccount", params);
            }
        }
        if (!invuseline.isNull("glcreditacct")) {
            final String[] params = { invuseline.getString("glcreditacct") };
            if (!fsr.validateFullGLAccount(this.getUserInfo(), invuseline.getString("glcreditacct"), this.getString("orgid"))) {
                throw new MXApplicationException("inventory", "InvalidGLAccount", params);
            }
        }
    }
    
    @Override
    public void modify() throws MXException, RemoteException {
        this.setValue("changedate", MXServer.getMXServer().getDate(), 11L);
        this.setValue("changeby", this.getUserName(), 11L);
    }
    
    @Override
    public String getUseType() throws MXException, RemoteException {
        final String useType = this.getTranslator().toInternalString("INVUSETYPE", this.getString("usetype"));
        return useType;
    }
    
    @Override
    public void validatedata() throws MXException, RemoteException {
        final InvUseLineSetRemote invuselineset = (InvUseLineSetRemote)this.getMboSet("INVUSELINE");
        int i = 0;
        if (invuselineset.isEmpty()) {
            return;
        }
        InvUseLine invuseline = null;
        final StringBuffer lines = new StringBuffer();
        while ((invuseline = (InvUseLine)invuselineset.getMbo(i)) != null) {
            if (!invuseline.toBeDeleted() && invuseline.getString("newphyscnt") != null) {
                invuseline.setValue("physcnt", invuseline.getInt("newphyscnt"), 2L);
                invuseline.setValue("physcntdate", invuseline.getDate("newphyscntdate"), 2L);
            }
            if (!invuseline.toBeDeleted() && invuseline.isTool() && invuseline.getString("issueto").equals("")) {
                lines.append(invuseline.getString("invuselinenum") + ", ");
            }
            ++i;
        }
        if (lines.length() > 0) {
            final Object[] params = { lines.delete(lines.length() - 2, lines.length() - 1) };
            throw new MXApplicationException("inventory", "RequiredIssueTo", params);
        }
    }
    
    @Override
    public boolean checkReturnLinesinSet() throws MXException, RemoteException {
        final MboSetRemote invUseLineSet = this.getMboSet("INVUSELINE");
        int i = 0;
        InvUseLine invUseLine = null;
        while ((invUseLine = (InvUseLine)invUseLineSet.getMbo(i)) != null) {
            if (invUseLine.isReturn()) {
                return true;
            }
            ++i;
        }
        return false;
    }
    
    public HashMap<Long, Double> getQtyMap() throws MXException, RemoteException {
        return this.qtyMap;
    }
    
    public void setQtyMap(final Long invBalId, final Double qty) throws MXException, RemoteException {
        this.qtyMap.put(invBalId, qty);
    }
    
    public void removeQtyMap(final Long invBalId) throws MXException, RemoteException {
        this.qtyMap.remove(invBalId);
    }
    
    public HashMap<Long, String> getUsedRotAssetNSMap() throws MXException, RemoteException {
        return this.usedRotAssetNSMap;
    }
    
    public void setUsedRotAssetNSMap(final Long keyid, final String rotassetnum) throws MXException, RemoteException {
        this.usedRotAssetNSMap.put(keyid, rotassetnum);
    }
    
    public void removeUsedRotAssetNSMap(final Long keyid) throws MXException, RemoteException {
        this.usedRotAssetNSMap.remove(keyid);
    }
    
    public HashMap<Long, String> getUsedRotAssetSMap() throws MXException, RemoteException {
        return this.usedRotAssetSMap;
    }
    
    public void setUsedRotAssetSMap(final Long keyid, final String rotassetnum) throws MXException, RemoteException {
        this.usedRotAssetSMap.put(keyid, rotassetnum);
    }
    
    public void removeUsedRotAssetSMap(final Long keyid) throws MXException, RemoteException {
        this.usedRotAssetSMap.remove(keyid);
    }
    
    public HashMap<Long, Double> getRotQtyMap() throws MXException, RemoteException {
        return this.rotQtyMap;
    }
    
    public void setRotQtyMap(final Long keyid, final Double qty) throws MXException, RemoteException {
        this.rotQtyMap.put(keyid, qty);
    }
    
    public void removeRotQtyMap(final Long keyid) throws MXException, RemoteException {
        if (this.rotQtyMap.containsKey(keyid)) {
            this.rotQtyMap.remove(keyid);
        }
    }
    
    public ArrayList<Long> getDeletedInvUseLineList() throws MXException, RemoteException {
        return this.deletedInvUseLineList;
    }
    
    public HashMap<Long, Double> getInvBalQtyNSMap() throws MXException, RemoteException {
        return this.invBalQtyNSMap;
    }
    
    public void setInvBalQtyNSMap(final Long invBalId, final Double qty) throws MXException, RemoteException {
        this.invBalQtyNSMap.put(invBalId, qty);
    }
    
    public void removeInvBalQtyNSMap(final Long invBalId) throws MXException, RemoteException {
        this.invBalQtyNSMap.remove(invBalId);
    }
    
    public ArrayList<String> getUsedRotAssetList() throws MXException, RemoteException {
        return this.usedRotAssetList;
    }
    
    public void addToUsedRotAssetList(final String rotassetnum) throws MXException, RemoteException {
        this.usedRotAssetList.add(rotassetnum);
    }
    
    public void checkRotAssetNumList(final long assetuid, final String rotassetnum) throws MXException, RemoteException {
        if (this.usedRotAssetNSMap.containsValue(rotassetnum)) {
            final Set<Map.Entry<Long, String>> setNS = this.usedRotAssetNSMap.entrySet();
            for (final Map.Entry<Long, String> usedRotAssetNS : setNS) {
                if (usedRotAssetNS.getValue().equalsIgnoreCase(rotassetnum) && usedRotAssetNS.getKey() != assetuid) {
                    throw new MXApplicationException("inventory", "usedRotAsset");
                }
            }
        }
        if (this.usedRotAssetSMap.containsValue(rotassetnum)) {
            final Set<Map.Entry<Long, String>> setS = this.usedRotAssetSMap.entrySet();
            for (final Map.Entry<Long, String> usedRotAssetS : setS) {
                if (usedRotAssetS.getValue().equalsIgnoreCase(rotassetnum) && usedRotAssetS.getKey() != assetuid) {
                    throw new MXApplicationException("inventory", "usedRotAsset");
                }
            }
        }
    }
    
    @Override
    public void canDelete() throws MXException, RemoteException {
        super.canDelete();
        if (!this.isEntered()) {
            throw new MXApplicationException("inventory", "cannotDeleteExistingRow");
        }
    }
    
    @Override
    public MboRemote setShipmentLines() throws MXException, RemoteException {
        final MboRemote shipment = this.getMboSet("SHIPMENT").add(2L);
        shipment.setValue("invusenum", this.getString("invusenum"), 2L);
        shipment.setValue("status", "NEW", 2L);
        shipment.setValue("fromsiteid", this.getString("siteid"), 2L);
        final MboSetRemote invUseLineSet = this.getMboSet("INVUSELINE");
        final MboRemote invUseLine = invUseLineSet.getMbo(0);
        if (invUseLine != null) {
            shipment.setPropagateKeyFlag(false);
            shipment.setValue("siteid", invUseLine.getString("tositeid"), 11L);
            final SiteServiceRemote siteService = (SiteServiceRemote)MXServer.getMXServer().lookup("SITE");
            final String org = siteService.getOrgForSite(shipment.getString("siteid"), shipment.getUserInfo());
            shipment.setValue("OrgId", org, 2L);
            shipment.setPropagateKeyFlag(true);
        }
        this.addShipmentLines(shipment);
        return shipment;
    }
    
    public void addShipmentLines(final MboRemote shipment) throws MXException, RemoteException {
        int i = 0;
        int j = 0;
        final MboSetRemote invUseLineSet = this.getMboSet("INVUSELINE");
        MboRemote invUseLine = null;
        MboRemote invUseLineSplit = null;
        final HashMap<Long, ArrayList<InvUseLineSplitRemote>> splitMap = this.getInvUseLineSplitRecordsMap();
        final MboSetRemote shipmentLineSet = shipment.getMboSet("SHIPMENTLINE");
        String description = null;
        int shipmentlinenumstart = 1;
        while ((invUseLine = invUseLineSet.getMbo(i)) != null) {
            if (invUseLine.toBeDeleted()) {
                ++i;
            }
            else {
                final MboRemote item = invUseLine.getMboSet("ITEM").getMbo(0);
                if (item != null) {
                    description = item.getString("description");
                }
                final MboRemote PO = ((InvUseLineRemote)invUseLine).getPO();
                final long keyId = invUseLine.getLong("invuselineid");
                final ArrayList<InvUseLineSplitRemote> invUseLineSplitList = splitMap.get(keyId);
                if (this.getUserInfo().isInteractive() && invUseLineSplitList != null && !invUseLineSplitList.isEmpty()) {
                    for (j = 0; j < invUseLineSplitList.size(); ++j) {
                        invUseLineSplit = invUseLineSplitList.get(j);
                        final MboRemote shipmentLine = shipmentLineSet.addAtEnd(2L);
                        shipmentLine.setValue("shipmentnum", shipment.getString("shipmentnum"), 2L);
                        shipmentLine.setValue("shipmentlinenum", Integer.toString(shipmentlinenumstart++), 11L);
                        shipmentLine.setValue("itemnum", invUseLineSplit.getString("itemnum"), 2L);
                        shipmentLine.setValue("itemdescription", description, 2L);
                        shipmentLine.setValue("itemsetid", invUseLineSplit.getString("itemsetid"), 2L);
                        shipmentLine.setValue("shippedqty", invUseLineSplit.getDouble("quantity"), 2L);
                        shipmentLine.setValue("fromstoreloc", invUseLine.getString("fromstoreloc"), 2L);
                        shipmentLine.setValue("tostoreloc", invUseLine.getString("tostoreloc"), 2L);
                        shipmentLine.setValue("fromsiteid", invUseLine.getString("siteid"), 2L);
                        shipmentLine.setValue("siteid", invUseLine.getString("tositeid"), 2L);
                        shipmentLine.setValue("toorgid", invUseLine.getString("toorgid"), 2L);
                        shipmentLine.setValue("fromorgid", invUseLine.getOwner().getString("orgid"), 2L);
                        shipmentLine.setValue("invuselinenum", invUseLine.getInt("invuselinenum"), 2L);
                        shipmentLine.setValue("invuselineid", invUseLine.getLong("invuselineid"), 2L);
                        shipmentLine.setValue("invuselinesplitid", invUseLineSplit.getLong("invuselinesplitid"), 2L);
                        shipmentLine.setValue("frombin", invUseLineSplit.getString("frombin"), 2L);
                        shipmentLine.setValue("fromlot", invUseLineSplit.getString("fromlot"), 2L);
                        shipmentLine.setValue("rotassetnum", invUseLineSplit.getString("rotassetnum"), 2L);
                        if (PO != null) {
                            shipmentLine.setValue("ponum", PO.getString("ponum"), 2L);
                            shipmentLine.setValue("vendor", PO.getString("vendor"), 2L);
                            shipmentLine.setValue("revisionnum", PO.getInt("revisionnum"), 2L);
                        }
                        final Long splitidKey = invUseLineSplit.getLong("invuselinesplitid");
                        shipmentLine.setValue("comments", invUseLine.getString("remark"), 2L);
                        this.shipmentLineMap.put(splitidKey, shipmentLine);
                    }
                }
                else {
                    j = 0;
                    final MboSetRemote invuselinesplitset = invUseLine.getMboSet("INVUSELINESPLIT");
                    MboRemote invuselinesplit = null;
                    while ((invuselinesplit = invuselinesplitset.getMbo(j)) != null) {
                        final MboRemote shipmentLine2 = shipmentLineSet.addAtEnd(2L);
                        shipmentLine2.setValue("shipmentlinenum", Integer.toString(shipmentlinenumstart++), 11L);
                        shipmentLine2.setValue("itemnum", invuselinesplit.getString("itemnum"), 2L);
                        shipmentLine2.setValue("itemdescription", description, 2L);
                        shipmentLine2.setValue("itemsetid", invuselinesplit.getString("itemsetid"), 2L);
                        shipmentLine2.setValue("shippedqty", invuselinesplit.getDouble("quantity"), 2L);
                        shipmentLine2.setValue("invuselinenum", invuselinesplit.getInt("invuselinenum"), 2L);
                        shipmentLine2.setValue("invuselineid", invUseLine.getLong("invuselineid"), 2L);
                        shipmentLine2.setValue("invuselinesplitid", invuselinesplit.getLong("invuselinesplitid"), 2L);
                        shipmentLine2.setValue("frombin", invuselinesplit.getString("frombin"), 2L);
                        shipmentLine2.setValue("fromlot", invuselinesplit.getString("fromlot"), 2L);
                        shipmentLine2.setValue("rotassetnum", invuselinesplit.getString("rotassetnum"), 2L);
                        shipmentLine2.setValue("fromstoreloc", invUseLine.getString("fromstoreloc"), 2L);
                        shipmentLine2.setValue("tostoreloc", invUseLine.getString("tostoreloc"), 2L);
                        shipmentLine2.setValue("fromsiteid", invUseLine.getString("siteid"), 2L);
                        shipmentLine2.setValue("siteid", invUseLine.getString("tositeid"), 2L);
                        shipmentLine2.setValue("fromorgid", invUseLine.getOwner().getString("orgid"), 2L);
                        shipmentLine2.setValue("toorgid", invUseLine.getString("toorgid"), 2L);
                        if (PO != null) {
                            shipmentLine2.setValue("ponum", PO.getString("ponum"), 2L);
                            shipmentLine2.setValue("vendor", PO.getString("vendor"), 2L);
                            shipmentLine2.setValue("revisionnum", PO.getInt("revisionnum"), 2L);
                        }
                        final Long splitidKey2 = invuselinesplit.getLong("invuselinesplitid");
                        shipmentLine2.setValue("comments", invUseLine.getString("remark"), 2L);
                        this.shipmentLineMap.put(splitidKey2, shipmentLine2);
                        ++j;
                    }
                }
                ++i;
            }
        }
    }
    
    public void validateForShipped(final MboRemote invUseLine) throws MXException, RemoteException {
        if (!((InvUseLine)invUseLine).isTransfer()) {
            final String transfer = this.getTranslator().toExternalDefaultValue("INVUSETYPE", "TRANSFER", this);
            final String issue = this.getTranslator().toExternalDefaultValue("INVUSETYPE", "ISSUE", this);
            final Object[] params = { issue, transfer };
            throw new MXApplicationException("inventory", "OnlyTransferLinesCanBeShipped", params);
        }
    }
    
    public String getClearingAcct() throws MXException, RemoteException {
        final SiteRemote siteMbo = (SiteRemote)this.getMboSet("$SITE" + this.getString("siteid"), "SITE", "siteid=:siteid").getMbo(0);
        final MboSetRemote orgSet = siteMbo.getMboSet("ORGANIZATION");
        final MboRemote orgmbo = orgSet.getMbo(0);
        return orgmbo.getString("clearingacct");
    }
    
    public HashMap<Long, MboRemote> getShipmentLineMap() throws MXException, RemoteException {
        return this.shipmentLineMap;
    }
    
    public void save() throws MXException, RemoteException {
        super.save();
        this.shipmentLineMap.clear();
        this.invUseLineList.clear();
        this.usedRotAssetList.clear();
        this.qtyMap.clear();
    }
    
    @Override
    public void updateInvUseReceipts(final InvUseLineRemote invUseLine) throws RemoteException, MXException {
        final MboSetRemote invUseLines = this.getMboSet("INVUSELINE");
        int count = 0;
        int complete = 0;
        boolean partial = false;
        String receiptsSyn = "";
        int i = 0;
        while (true) {
            InvUseLineRemote oneLine = (InvUseLineRemote)invUseLines.getMbo(i);
            if (oneLine == null) {
                break;
            }
            if (invUseLine != null && oneLine.getString("invusenum").equals(invUseLine.getString("invusenum")) && oneLine.getString("invuselinenum").equals(invUseLine.getString("invuselinenum"))) {
                oneLine = invUseLine;
            }
            else if (!this.invUseLineMap.isEmpty() && this.invUseLineMap.containsKey(oneLine.getLong("invuselineid"))) {
                oneLine = (InvUseLineRemote) this.invUseLineMap.get(oneLine.getLong("invuselineid"));
            }
            if (!oneLine.toBeDeleted()) {
                final double orderqty = oneLine.getDouble("quantity");
                final double receivedqty = oneLine.getDouble("receivedqty");
                final double returnedqty = MXMath.abs(oneLine.getDouble("returnedqty"));
                ++count;
                if (oneLine.getBoolean("receiptscomplete")) {
                    ++complete;
                }
                else if (orderqty > 0.0) {
                    if (MXMath.add(receivedqty, returnedqty) > 0.0 && MXMath.add(receivedqty, returnedqty) < orderqty) {
                        partial = true;
                        break;
                    }
                    if (receivedqty - returnedqty >= orderqty) {
                        ++complete;
                    }
                }
                else if (orderqty < 0.0) {
                    if (MXMath.subtract(receivedqty, returnedqty) != 0.0 && MXMath.subtract(receivedqty, returnedqty) > orderqty) {
                        partial = true;
                        break;
                    }
                    if (MXMath.subtract(receivedqty, returnedqty) <= orderqty) {
                        ++complete;
                    }
                }
                if (complete > 0 && complete < count) {
                    break;
                }
            }
            ++i;
        }
        if (count > 0) {
            if (complete >= count) {
                receiptsSyn = "COMPLETE";
            }
            else if (complete > 0 || partial) {
                receiptsSyn = "PARTIAL";
            }
            else {
                receiptsSyn = "NONE";
            }
        }
        else {
            receiptsSyn = "NONE";
        }
        final String receipts = this.getTranslator().toExternalDefaultValue("RECEIPTS", receiptsSyn, this);
        this.setValue("receipts", receipts, 2L);
    }
    
    public double getExchangeRate(final Date date) throws MXException, RemoteException {
        cust.component.Logger.Log("InvUse.getExchangeRate");
    	final MboRemote invUseLine = this.getMboSet("INVUSELINE").getMbo(0);
        if (invUseLine == null) {
            return 1.0;
        }
        final String toOrgid = invUseLine.getString("toorgid");
        if (toOrgid.equalsIgnoreCase(this.getString("orgid"))) {
            return 1.0;
        }
        final CurrencyServiceRemote currService = (CurrencyServiceRemote)MXServer.getMXServer().lookup("CURRENCY");
        final String currencyCodeTo = currService.getBaseCurrency1(toOrgid, this.getUserInfo());
        if (currencyCodeTo.equals("")) {
            return 1.0;
        }
        final String currency = this.getString("currencycode");
        if (currency.equals("")) {
            return 1.0;
        }
        return currService.getCurrencyExchangeRate(this.getUserInfo(), currency, currencyCodeTo, MXServer.getMXServer().getDate(this.getClientLocale(), this.getClientTimeZone()), this.getString("orgid"));
    }
    
    public double getExchangeRate2(final Date date) throws MXException, RemoteException {
        cust.component.Logger.Log("InvUse.getExchangeRate2");
        final MboRemote invUseLine = this.getMboSet("INVUSELINE").getMbo(0);
        if (invUseLine == null) {
            return 1.0;
        }
        final String toOrgid = invUseLine.getString("toorgid");
        final CurrencyServiceRemote currService = (CurrencyServiceRemote)MXServer.getMXServer().lookup("CURRENCY");
        final String baseCurrency2 = currService.getBaseCurrency2(toOrgid, this.getUserInfo());
        if (baseCurrency2.equals("")) {
            return 0.0;
        }
        final String currency = this.getString("currencycode");
        if (baseCurrency2.equalsIgnoreCase(currency)) {
            return 1.0;
        }
        if (currency.equals("")) {
            return 0.0;
        }
        return currService.getCurrencyExchangeRate(this.getUserInfo(), currency, baseCurrency2, MXServer.getMXServer().getDate(this.getClientLocale(), this.getClientTimeZone()), this.getString("orgid"));
    }
    
    public HashMap<Long, MboRemote> getInvUseLineMap() throws MXException, RemoteException {
        return this.invUseLineMap;
    }
    
    public void validateHardReservation(final MboRemote invUseLine) throws RemoteException, MXException {
        final MboSet invSet = (MboSet)invUseLine.getMboSet("INVENTORY");
        if (invSet != null) {
            final MboRemote inv = invSet.getMbo(0);
            if (inv != null && inv.getBoolean("HARDRESISSUE")) {
                final MboSetRemote invReserveSet = invUseLine.getMboSet("INVRESERVE");
                if (invReserveSet == null) {
                    final Object[] params = { invUseLine.getInt("invuselinenum") };
                    throw new MXApplicationException("INVUSE", "HARDINVENTORY", params);
                }
                final MboRemote invReserve = invReserveSet.getMbo(0);
                if (invReserve == null) {
                    final Object[] params2 = { invUseLine.getInt("invuselinenum") };
                    throw new MXApplicationException("INVUSE", "HARDINVENTORY", params2);
                }
                final String restype = this.getTranslator().toInternalString("RESTYPE", invReserve.getString("RESTYPE"));
                if (restype.compareToIgnoreCase("APSOFT") == 0 || restype.compareToIgnoreCase("SOFT") == 0) {
                    final Object[] params3 = { invUseLine.getInt("invuselinenum") };
                    throw new MXApplicationException("INVUSE", "HARDINVENTORY", params3);
                }
            }
        }
    }
    
    public int getEvaluateSplitFlag() throws MXException, RemoteException {
        return this.evaluateSplit;
    }
    
    public int setEvaluateSplitFlag(final int split) throws MXException, RemoteException {
        return this.evaluateSplit = split;
    }
}