package psdi.app.inventory;

import psdi.mbo.Translate;
import psdi.app.common.TransactionGLMerger;
import java.util.Iterator;
import java.util.Map;
import psdi.mbo.MboSetInfo;
import java.util.Arrays;
import psdi.mbo.MboValueInfo;
import psdi.app.meter.DeployedMeterRemote;
import java.util.Vector;
import psdi.app.workorder.WOSet;
import psdi.security.UserInfo;
import psdi.app.invoice.InvoiceServiceRemote;
import psdi.util.MXMath;
import psdi.mbo.MboValue;
import psdi.app.integration.IntegrationServiceRemote;
import psdi.app.location.LocationRemote;
import psdi.mbo.SqlFormat;
import psdi.app.financial.FinancialServiceRemote;
import java.util.Calendar;
import psdi.app.item.ItemRemote;
import psdi.util.MXApplicationException;
import java.util.Date;
import psdi.server.AppService;
import psdi.app.currency.CurrencyService;
import psdi.server.MXServer;
import psdi.txn.MXTransaction;
import psdi.mbo.MboSetRemote;
import psdi.app.workorder.WORemote;
import psdi.txn.Transactable;
import java.rmi.RemoteException;
import psdi.util.MXException;
import psdi.mbo.MboSet;
import psdi.app.asset.AssetRemote;
import psdi.mbo.MboRemote;
import psdi.mbo.Mbo;

public class MatUseTrans extends Mbo implements MatUseTransRemote
{
    private MboRemote issueMbo;
    double remainQty;
    double totalQty;
    double returnQtyDeleted;
    boolean sharedInventoryHasBeenUpdated;
    boolean sharedInvBalancesHasBeenUpdated;
    boolean invReserveUpdated;
    boolean checkNegBalance;
    boolean selectItemForReturn;
    private boolean uncommitted;
    boolean processed;
    private boolean issueTypeHasChanged;
    private boolean ignoreAssetLocMismatch;
    private boolean ignoreLocOccupied;
    private boolean updateWO;
    private AssetRemote rotAssetRec;
    boolean workOrderUpdated;
    
    public MatUseTrans(final MboSet ms) throws MXException, RemoteException {
        super(ms);
        this.issueMbo = null;
        this.remainQty = -1.11;
        this.totalQty = 0.0;
        this.returnQtyDeleted = 0.0;
        this.sharedInventoryHasBeenUpdated = false;
        this.sharedInvBalancesHasBeenUpdated = false;
        this.invReserveUpdated = false;
        this.checkNegBalance = true;
        this.selectItemForReturn = false;
        this.uncommitted = false;
        this.processed = false;
        this.issueTypeHasChanged = false;
        this.ignoreAssetLocMismatch = true;
        this.ignoreLocOccupied = true;
        this.updateWO = true;
        this.rotAssetRec = null;
        this.workOrderUpdated = false;
    }
    
    @Override
    public void init() throws MXException {
        super.init();
        try {
            final MboSetRemote matUseSet = this.getThisMboSet();
            final MXTransaction trn = matUseSet.getMXTransaction();
            trn.setIndexOf(matUseSet, 0);
        }
        catch (Exception ex) {}
        this.setFieldFlag("TRANSDATE", 7L, true);
        this.setFieldFlag("qtyrequested", 7L, true);
        this.setFieldFlag("qtyrequested", 128L, true);
        this.setFieldFlag("CURBAL", 7L, true);
        this.setFieldFlag("CURBAL", 128L, true);
        this.setFieldFlag("PHYSCNT", 7L, true);
        this.setFieldFlag("PHYSCNT", 128L, true);
        this.setFieldFlag("linecost", 7L, true);
        this.setFieldFlag("UNITCOST", 128L, true);
        this.setFieldFlag("linecost", 512L, true);
        final MboRemote owner = this.getOwner();
        if (owner != null && owner instanceof WORemote) {
            this.setFieldFlag("mrnum", 7L, true);
            this.setFieldFlag("mrlinenum", 7L, true);
        }
        if (!this.toBeAdded()) {
            this.setFlag(7L, true);
        }
    }
    
    @Override
    public void add() throws MXException, RemoteException {
        final MboRemote owner = this.getOwner();
        if (owner != null) {
            this.setValue("tositeid", owner.getString("siteid"), 11L);
        }
        else {
            this.setValue("tositeid", this.getString("siteid"), 11L);
        }
        this.setValue("sourcembo", "MATUSETRANS", 3L);
        final String quantity = this.getMboValue("quantity").getMboValueInfo().getDefaultValue();
        if (quantity != null) {
            this.setValue("quantity", Double.parseDouble(quantity), 3L);
        }
        else {
            this.setValue("quantity", -1, 3L);
        }
        this.setValue("ISSUETYPE", this.getTranslator().toExternalDefaultValue("ISSUETYP", "ISSUE", this), 11L);
        final String lineType = this.getMboValue("linetype").getMboValueInfo().getDefaultValue();
        if (lineType != null) {
            this.setValue("LINETYPE", lineType, 3L);
        }
        else {
            this.setValue("LINETYPE", this.getTranslator().toExternalDefaultValue("LINETYPE", "ITEM", this), 3L);
        }
        final Date currentDate = MXServer.getMXServer().getDate();
        this.setValue("ACTUALDATE", currentDate, 2L);
        this.setValue("NEWPHYSCNTDATE", currentDate, 2L);
        this.setValue("TRANSDATE", currentDate, 2L);
        this.setValue("ENTERBY", this.getUserInfo().getUserName(), 2L);
        this.setValue("SPAREPARTADDED", false, 2L);
        this.setValue("rollup", false, 2L);
        this.setValue("CONDRATE", 100, 2L);
        this.setValue("CURBAL", 0, 2L);
        this.setValue("PHYSCNT", 0, 2L);
        final CurrencyService curService = (CurrencyService)((AppService)this.getMboServer()).getMXServer().lookup("CURRENCY");
        this.setValue("CURRENCYCODE", curService.getBaseCurrency1(this.getString("orgid"), this.getUserInfo()));
        final MboRemote ownerRem = this.getOwner();
        if (ownerRem.isBasedOn("INVENTORY")) {
            this.setValue("itemsetid", owner.getString("itemsetid"), 2L);
            this.setValue("itemnum", owner.getString("itemnum"), 2L);
            this.setValue("storeloc", owner.getString("location"), 2L);
        }
        else if (ownerRem.isBasedOn("LOCATIONS")) {
            this.setValue("storeloc", owner.getString("location"), 2L);
            this.setValue("UNITCOST", 0, 2L);
            this.setFieldFlag("UNITCOST", 7L, true);
        }
        else if (ownerRem.isBasedOn("WORKORDER")) {
            this.setValue("wonum", owner.getString("wonum"), 2L);
            this.setValue("fincntrlid", owner.getString("fincntrlid"), 2L);
        }
        else if (ownerRem.isBasedOn("MATRECTRANS")) {
            this.addFromMatRec();
        }
        if (owner != null && owner.isBasedOn("ASSET")) {
            this.setValue("assetnum", owner.getString("assetnum"), 11L);
            this.setValue("tositeid", owner.getString("siteid"), 11L);
            this.setValue("location", owner.getString("location"), 11L);
            this.setFieldFlag("assetnum", 7L, true);
            this.setFieldFlag("tositeid", 7L, true);
            this.setFieldFlag("location", 7L, true);
            if (((AssetRemote)owner).getDefSiteId() != null && ((AssetRemote)owner).getDefStoreloc() != null) {
                this.setValue("siteid", ((AssetRemote)owner).getDefSiteId(), 2L);
                this.setValue("storeloc", ((AssetRemote)owner).getDefStoreloc(), 2L);
            }
        }
    }
    
    private void addFromMatRec() throws MXException, RemoteException {
        final MatRecTrans owner = (MatRecTrans)this.getOwner();
        double quantity = owner.getDouble("quantity");
        if (owner.getPOLine().isInspectionRequired() && !owner.isReturn() && !owner.isVoidReceipt()) {
            quantity = (owner.getDouble("inspectedqty") - owner.getDouble("rejectqty")) * -1.0;
        }
        else if (!owner.isMisclReceipt()) {
            quantity *= -1.0;
        }
        final double unitcost = owner.getDouble("unitcost");
        final String matRecLineType = this.getTranslator().toInternalString("LINETYPE", owner.getString("linetype"));
        if (matRecLineType.equalsIgnoreCase("SPORDER")) {
            this.setValue("linetype", this.getTranslator().toExternalDefaultValue("LINETYPE", "ITEM", this), 2L);
        }
        else {
            this.setValue("linetype", owner.getString("linetype"), 2L);
        }
        this.setValue("itemsetid", owner.getString("itemsetid"), 2L);
        this.setValue("itemnum", owner.getString("itemnum"), 2L);
        this.setValue("conditioncode", owner.getString("conditioncode"), 2L);
        this.setValue("description", owner.getString("description"), 2L);
        this.setValue("enteredastask", owner.getString("enteredastask"), 2L);
        this.setValueNull("storeloc", 2L);
        this.setValueNull("binnum", 2L);
        this.setValueNull("lotnum", 2L);
        this.setValue("glcreditacct", owner.getString("glcreditacct"), 11L);
        if (owner.isBasedOn("MATRECTRANS") && this.getTranslator().toInternalString("ISSUETYP", owner.getString("issuetype")).equalsIgnoreCase("INVOICE")) {
            this.setValue("gldebitacct", owner.getString("gldebitacct"), 11L);
        }
        else {
            this.setValue("gldebitacct", owner.getPOLine().getString("gldebitacct"), 11L);
        }
        this.setValue("commodity", owner.getString("commodity"), 11L);
        this.setValue("commoditygroup", owner.getString("commoditygroup"), 11L);
        this.setValue("ponum", owner.getString("ponum"), 2L);
        this.setValue("porevisionnum", owner.getString("porevisionnum"), 2L);
        this.setValue("polinenum", owner.getString("polinenum"), 2L);
        this.setValue("it1", owner.getString("it1"), 2L);
        this.setValue("it2", owner.getString("it2"), 2L);
        this.setValue("it3", owner.getString("it3"), 2L);
        this.setValue("it4", owner.getString("it4"), 2L);
        this.setValue("it5", owner.getString("it5"), 2L);
        this.setValue("it6", owner.getString("it6"), 2L);
        this.setValue("it7", owner.getString("it7"), 2L);
        this.setValue("it8", owner.getString("it8"), 2L);
        this.setValue("it9", owner.getString("it9"), 2L);
        this.setValue("it10", owner.getString("it10"), 2L);
        this.setValue("itin1", owner.getString("itin1"), 2L);
        this.setValue("itin2", owner.getString("itin2"), 2L);
        this.setValue("itin3", owner.getString("itin3"), 2L);
        this.setValue("itin4", owner.getString("itin4"), 2L);
        this.setValue("itin5", owner.getString("itin5"), 2L);
        this.setValue("itin6", owner.getString("itin6"), 2L);
        this.setValue("itin7", owner.getString("itin7"), 2L);
        this.setValue("packingslipnum", owner.getString("packingslipnum"), 2L);
        String issueType = null;
        if (quantity == 0.0 && this.getTranslator().toInternalString("ISSUETYP", owner.getString("issuetype")).equalsIgnoreCase("INVOICE")) {
            issueType = this.getTranslator().toExternalDefaultValue("ISSUETYP", "INVOICE", this);
            this.setFieldFlag("issueto", 128L, false);
        }
        else if (quantity < 0.0) {
            issueType = this.getTranslator().toExternalDefaultValue("ISSUETYP", "ISSUE", this);
        }
        else {
            issueType = this.getTranslator().toExternalDefaultValue("ISSUETYP", owner.getString("issuetype"), this);
        }
        this.setValue("issuetype", issueType, 2L);
        this.setValue("matrectransid", owner.getString("matrectransid"), 2L);
        this.setValue("enterby", owner.getString("enterby"), 2L);
        if (issueType.equalsIgnoreCase("RETURN") || owner.isVoidReceipt()) {
            this.setFieldFlag("issueto", 128L, false);
        }
        this.setValue("issueto", owner.getString("issueto"), 2L);
        this.setValue("outside", owner.getBoolean("outside"), 2L);
        this.setValue("rollup", false, 2L);
        this.setValue("sparepartadded", false, 2L);
        if (owner.getDouble("qtyrequested") < 0.0) {
            this.getMboValue("qtyrequested").getMboValueInfo().setPositive(false);
        }
        this.setValue("qtyrequested", owner.getDouble("qtyrequested"), 2L);
        this.setValue("actualcost", owner.getDouble("actualcost"), 2L);
        this.setValue("actualdate", owner.getString("actualdate"), 3L);
        this.setValue("financialperiod", owner.getString("financialperiod"), 11L);
        this.setValue("exchangerate", owner.getDouble("exchangerate"), 2L);
        if (!owner.getMboValue("exchangerate2").isNull()) {
            this.setValue("exchangerate2", owner.getDouble("exchangerate2"), 2L);
        }
        this.setValue("quantity", quantity, 2L);
        this.setValue("unitcost", unitcost, 2L);
        if (!owner.isNull("conversion")) {
            this.setValue("conversion", owner.getDouble("conversion"), 2L);
        }
        this.setValue("currencycode", owner.getString("currencycode"), 2L);
        if (owner.getPOLine().isInspectionRequired()) {
            if (issueType.equalsIgnoreCase("INVOICE")) {
                this.setValue("currencylinecost", owner.getDouble("currencylinecost"), 2L);
            }
            else {
                this.setValue("currencylinecost", quantity * -1.0 * unitcost, 2L);
            }
        }
        else {
            this.setValue("currencylinecost", owner.getDouble("currencylinecost"), 2L);
        }
        this.setValue("currencyunitcost", owner.getDouble("currencyunitcost"), 2L);
        this.setValue("matrectransid", owner.getString("matrectransid"), 2L);
        if (owner.getPOLine().isInspectionRequired()) {
            if (issueType.equalsIgnoreCase("INVOICE")) {
                this.setValue("linecost", owner.getDouble("loadedcost"), 2L);
            }
            else {
                this.setValue("linecost", quantity * -1.0 * unitcost, 2L);
            }
        }
        else {
            this.setValue("linecost", owner.getDouble("loadedcost"), 2L);
        }
        if (!this.isNull("exchangerate2")) {
            this.setValue("linecost2", this.getDouble("exchangerate2") * this.getDouble("linecost"), 2L);
        }
        this.setValue("assetnum", owner.getString("assetnum"), 11L);
        final MatRecTrans matrec = owner;
        this.setValue("rotassetnum", matrec.getRotAssetNum(), 11L);
        this.setValue("mrlinenum", owner.getString("mrlinenum"), 11L);
        this.setValue("mrnum", owner.getString("mrnum"), 11L);
        this.setValue("wonum", owner.getString("wonum"), 11L);
        this.setValue("taskid", owner.getString("taskid"), 11L);
        this.setValue("refwo", owner.getString("refwo"), 11L);
        this.setValue("fincntrlid", owner.getString("fincntrlid"), 2L);
        this.setValue("location", owner.getString("location"), 11L);
        this.setValue("ownersysid", owner.getString("ownersysid"), 2L);
        this.setValue("binnum", owner.getString("tobin"), 3L);
        this.setValue("lotnum", owner.getString("tolot"), 3L);
        if (issueType.equalsIgnoreCase("INVOICE")) {
            this.setValue("financialperiod", owner.getString("financialperiod"), 11L);
        }
        if (owner.isMisclReceipt()) {
            this.setValue("storeloc", owner.getString("tostoreloc"), 11L);
            this.setValue("curbal", owner.getDouble("curbal"), 11L);
        }
        if (!owner.isNull("positeid") && !owner.getString("positeid").equalsIgnoreCase(this.getString("siteid"))) {
            this.setValue("siteid", owner.getString("fromsiteid"), 66L);
        }
    }
    
    public double getTotalQtyForReturn() throws MXException, RemoteException {
        if (!this.isIssue()) {
            return 0.0;
        }
        final MboSetRemote matUseSet = this.getMboSet("RETURNS");
        return Math.abs(this.getDouble("quantity")) - Math.abs(matUseSet.sum("quantity"));
    }
    
    @Override
    public double getQtyForReturn() throws MXException, RemoteException {
        return this.remainQty;
    }
    
    @Override
    public void setQtyForReturn(final double qty) throws MXException, RemoteException {
        this.remainQty += qty;
    }
    
    public MatUseTransRemote getIssueForThisReturn() throws MXException, RemoteException {
        return (MatUseTransRemote)this.issueMbo;
    }
    
    public double getTotalQty() throws MXException, RemoteException {
        return this.totalQty;
    }
    
    @Override
    public void setTotalQtyForThisReturn(final double qty) throws MXException, RemoteException {
        this.totalQty = qty;
    }
    
    @Override
    public void canDelete() throws MXException, RemoteException {
        if (this.toBeAdded()) {
            return;
        }
        throw new MXApplicationException("inventory", "matusetransCannotDelete");
    }
    
    @Override
    public void appValidate() throws MXException, RemoteException {
        boolean isToolInv = false;
        final MboRemote owner = this.getOwner();
        if (owner != null && owner instanceof ToolInv) {
            isToolInv = true;
        }
        super.appValidate();
        if (this.isIssue()) {
            final InventoryRemote invRem = (InventoryRemote)this.getSharedInventory();
            if (invRem != null) {
                invRem.checkRequestAgainstItemMaxIssue(this.getString("assetnum"), -this.getDouble("quantity"));
            }
        }
        if (this.isIssue() && !this.toBeAdded()) {
            super.save();
            return;
        }
        if (this.isReturn() && !this.toBeAdded()) {
            return;
        }
        if (!this.toBeDeleted()) {
            if (this.isNull("itemnum") && this.isNull("description")) {
                throw new MXApplicationException("inventory", "enterItemOrDesc");
            }
            if (this.isNull("itemnum") && !this.isNull("storeloc")) {
                throw new MXApplicationException("inventory", "enterValidItemnum");
            }
            if (!isToolInv && !this.getTranslator().toInternalString("LINETYPE", this.getString("linetype")).equals("TOOL") && this.isNull("refwo") && this.isNull("assetnum") && this.isNull("location") && this.isNull("gldebitacct") && this.isNull("mrnum")) {
                throw new MXApplicationException("inventory", "matusetransNullChargeTo");
            }
        }
        if (!this.isNull("itemnum") && this.isNull("storeloc") && (this.getOwner() == null || !this.getOwner().getName().equals("MATRECTRANS"))) {
            final ItemRemote item = (ItemRemote)this.getMboSet("ITEM").getMbo(0);
            final Mbo itemOrg = (Mbo)item.getMboSet("itemorginfo").getMbo(0);
            if (itemOrg != null) {
                final String itemOrgCategory = this.getTranslator().toInternalString("CATEGORY", itemOrg.getString("category"));
                if (!this.getTranslator().toInternalString("LINETYPE", this.getString("linetype")).equals("SPORDER") && itemOrgCategory != null && !itemOrgCategory.equals("NS") && !itemOrgCategory.equals("SP")) {
                    throw new MXApplicationException("inventory", "cannotIssueNoStoreloc");
                }
            }
        }
        if (!this.getString("mrnum").equals("") && this.getString("mrlinenum").equals("")) {
            throw new MXApplicationException("inventory", "specifyReqLineNum");
        }
        if (!this.getString("mrlinenum").equals("") && this.getString("mrnum").equals("")) {
            throw new MXApplicationException("inventory", "specifyReqNum");
        }
        if (!this.isNull("itemnum") && !this.getTranslator().toInternalString("LINETYPE", this.getString("linetype")).equals("SPORDER") && owner != null && !owner.isBasedOn("InvUse")) {
            final ItemRemote item = (ItemRemote)this.getMboSet("ITEM").getMbo(0);
            if (item.isRotating() && this.isNull("rotassetnum") && (this.getOwner() == null || !(this.getOwner() instanceof MatRecTrans))) {
                throw new MXApplicationException("inventory", "matusetransNullRotassetnum");
            }
            if (!this.isNull("rotassetnum") && this.isIssue()) {
                final MboRemote rotasset = this.getMboSet("rotasset").getMbo(0);
                if (rotasset != null && !this.getString("binnum").equalsIgnoreCase(rotasset.getString("binnum"))) {
                    final Object[] param = { "Item/Rotating Asset/Bin combination", "" };
                    throw new MXApplicationException("commlog", "InvalidTemplateId", param);
                }
            }
            if (item.isConditionEnabled() && this.isNull("conditioncode")) {
                final Object[] param2 = { this.getString("itemnum") };
                throw new MXApplicationException("inventory", "noConditionCode", param2);
            }
        }
        if (this.getDouble("quantity") == 0.0 && !this.getTranslator().toInternalString("ISSUETYP", this.getString("issuetype")).equalsIgnoreCase("INVOICE")) {
            throw new MXApplicationException("inventory", "matusetransZeroQuantity");
        }
        if (!this.isNull("issuetype")) {
            if (this.isIssue() && this.getDouble("quantity") > 0.0) {
                throw new MXApplicationException("inventory", "matusetransPosQuantity");
            }
            if (this.isReturn() && this.getDouble("quantity") < 0.0) {
                throw new MXApplicationException("inventory", "matusetransNegQuantity");
            }
        }
        if (!this.isNull("itemnum") && !this.isNull("storeloc")) {
            final MboRemote inventory = this.getSharedInventory();
            if (inventory == null) {
                throw new MXApplicationException("inventory", "invbalNotInInventory");
            }
            final MboRemote invBal = this.getSharedInvBalance();
            if (invBal == null && this.isIssue()) {
                throw new MXApplicationException("inventory", "matusetransNoBalances");
            }
            if (invBal != null) {
                final Date currentUseBy = invBal.getDate("useby");
                Calendar currentUseByCal = null;
                if (currentUseBy != null) {
                    currentUseByCal = Calendar.getInstance();
                    currentUseByCal.setTime(currentUseBy);
                    final Date now = MXServer.getMXServer().getDate();
                    final Calendar nowCal = Calendar.getInstance();
                    nowCal.setTime(now);
                    nowCal.set(11, 0);
                    nowCal.set(12, 0);
                    nowCal.set(13, 0);
                    nowCal.set(14, 0);
                    if (currentUseByCal.before(nowCal)) {
                        throw new MXApplicationException("inventory", "expiredLot");
                    }
                }
            }
            if (this.toBeAdded() && this.isIssue() && owner != null && !(owner instanceof MatRecTransRemote) && this.getCheckNegBalanceFlag()) {
                this.checkForNegativeBalance();
            }
        }
        boolean noFinancial = false;
        if ((owner != null && owner.isBasedOn("InvUse")) || ((this.isIssue() || this.isReturn()) && this.getTranslator().toInternalString("LINETYPE", this.getString("linetype")).equals("TOOL"))) {
            noFinancial = true;
        }
        final FinancialServiceRemote fsr = (FinancialServiceRemote)MXServer.getMXServer().lookup("FINANCIAL");
        if (!isToolInv && !noFinancial) {
            if (fsr.glRequiredForTrans(this.getUserInfo(), this.getString("orgid")) && (this.isNull("gldebitacct") || this.isNull("glcreditacct"))) {
                throw new MXApplicationException("financial", "GLRequiredForTrans");
            }
            if (!this.toBeDeleted()) {
                final String glDebitAcct = this.getString("gldebitacct");
                final String orgID = this.getString("orgid");
                if (!glDebitAcct.equals("")) {
                    final String[] params = { glDebitAcct };
                    if (!fsr.validateFullGLAccount(this.getUserInfo(), glDebitAcct, orgID)) {
                        throw new MXApplicationException("inventory", "InvalidGLAccount", params);
                    }
                }
                if (!this.isNull("glcreditacct")) {
                    final String[] params = { this.getString("glcreditacct") };
                    if (!fsr.validateFullGLAccount(this.getUserInfo(), this.getString("glcreditacct"), orgID)) {
                        throw new MXApplicationException("inventory", "InvalidGLAccount", params);
                    }
                }
            }
        }
        if (!this.isNull("RefWO")) {
            this.verifyChangesAreAllowed();
            boolean editHistMode = false;
            if (owner != null && owner.isBasedOn("WORKORDER")) {
                final WORemote woOwner = (WORemote)owner;
                editHistMode = woOwner.isWOInEditHist();
            }
            if (this.getWO().getBoolean("HistoryFlag") && this.isIssue() && !editHistMode) {
                final Object[] param3 = { this.getString("refwo"), this.getMboValue("REFWO").getColumnTitle() };
                throw new MXApplicationException("workorder", "WOHistory", param3);
            }
        }
    }
    
    private void verifyChangesAreAllowed() throws MXException, RemoteException {
        final MboRemote workorder = this.getWO();
        if (workorder == null) {
            return;
        }
        if (!this.isNull("taskid")) {
            final MboSetRemote woChildren = workorder.getMboSet("CHILDREN");
            int y = 0;
            for (MboRemote woChild = woChildren.getMbo(y); woChild != null; woChild = woChildren.getMbo(y)) {
                if (woChild.getBoolean("ISTASK") && this.getString("taskid").equals(woChild.getString("TASKID")) && !woChild.getBoolean("woacceptscharges")) {
                    final Object[] param = { workorder.getString("wonum"), this.getString("taskid") };
                    throw new MXApplicationException("workorder", "TaskAcceptsChargesNo", param);
                }
                ++y;
            }
        }
        else if (!workorder.getBoolean("woacceptscharges")) {
            final Object[] param2 = { workorder.getString("wonum") };
            throw new MXApplicationException("workorder", "WOAcceptsChargesNo", param2);
        }
    }
    
    @Override
    public MboRemote getWO() throws MXException, RemoteException {
        final MboRemote owner = this.getOwner();
        if (owner != null && owner instanceof WORemote) {
            return owner;
        }
        if (this.isNull("refwo")) {
            return null;
        }
        final SqlFormat sqf = new SqlFormat(this, "wonum=:refwo and siteid=:tositeid");
        final MboSetRemote woset = ((MboSet)this.getThisMboSet()).getSharedMboSet("WORKORDER", sqf.format());
        MboRemote woMbo = null;
        if (!woset.isEmpty()) {
            woMbo = woset.getMbo(0);
        }
        return woMbo;
    }
    
    public void save() throws MXException, RemoteException {
        final MboRemote owner = this.getOwner();
        final Inventory invMbo = (Inventory)this.getSharedInventory();
        if (invMbo != null) {
            this.setValue("consignment", invMbo.getBoolean("consignment"), 2L);
            this.setValue("consvendor", invMbo.getString("consvendor"), 2L);
            if (!this.getBoolean("split") && owner != null && (owner.isBasedOn("WORKORDER") || owner.isBasedOn("ASSET")) && (invMbo.getCostType().equalsIgnoreCase("LIFO") || invMbo.getCostType().equalsIgnoreCase("FIFO"))) {
                this.createMatUseTransRecordforLifoFifo(invMbo);
            }
        }
        if (this.isKitting()) {
            super.save();
            return;
        }
        if ((!this.isNull("assetnum") || !this.isNull("location")) && !this.isNull("itemnum")) {
            this.handleMeterReadingOnIssue();
        }
        if (owner != null && owner.getName().equals("MATRECTRANS")) {
            if (((MatRecTrans)owner).isMisclReceipt() && this.isReturn() && invMbo != null) {
                invMbo.updateInventoryIssueDetails(this.getDate("actualdate"), this.getDouble("quantity"));
            }
            super.save();
            return;
        }
        if (!this.isNull("ponum") || (owner != null && this.getOwner().getName().equals("MATRECTRANS"))) {
            super.save();
            return;
        }
        if (this.isIssue() && !this.toBeAdded()) {
            super.save();
            return;
        }
        if (this.isReturn() && !this.toBeAdded()) {
            super.save();
            return;
        }
        if (this.isReturn() && !this.toBeDeleted() && this.issueMbo != null) {
            final SqlFormat sqf = new SqlFormat(this, "matusetransid=:1");
            sqf.setLong(1, this.getLong("issueid"));
            MboRemote issue = null;
            issue = ((MboSet)this.getThisMboSet()).getSharedMboSet("MATUSETRANS", sqf.format()).getMbo(0);
            if (issue != null) {
                issue.setValue("QTYRETURNED", issue.getDouble("qtyreturned") + Math.abs(this.getDouble("quantity")), 2L);
            }
        }
        this.updateWorkOrder();
        if (!this.isNull("rotassetnum")) {
            if (this.isIssue() && !this.isNull("location")) {
                final LocationRemote location = (LocationRemote)this.getMboSet("LOCATION").getMbo(0);
                if (location.isStore()) {
                    final Object[] params = { this.getString("itemnum"), this.getString("location") };
                    throw new MXApplicationException("inventory", "rotItemIssueToStore", params);
                }
            }
            this.moveAsset();
        }
        if (!this.isNull("assetnum")) {
            this.updateAssetSparePart();
        }
        final MboValue mbv = this.getMboValue("requestnum");
        if (!this.invReserveUpdated && !mbv.isNull()) {
            this.updateInvReserve();
        }
        if (this.isNull("itemnum") || this.isNull("storeloc")) {
            this.setUncommitted(true);
            super.save();
            return;
        }
        InvBalances invBalMbo = (InvBalances)this.getSharedInvBalance();
        InvCost invcost = (InvCost)this.getInvCostRecord(invMbo);
        final IntegrationServiceRemote intserv = (IntegrationServiceRemote)((AppService)this.getMboServer()).getMXServer().lookup("INTEGRATION");
        final boolean useIntegration = intserv.useIntegrationRules(this.getString("ownersysid"), invMbo.getString("ownersysid"), "INV", this.getUserInfo());
        if (this.isReturn()) {
            if ((invMbo.getCostType().equals("LIFO") || invMbo.getCostType().equals("FIFO")) && !useIntegration) {
                invMbo.addInvLifoFifoCostRecord(this.getDouble("quantity"), this.getDouble("unitcost"), this.getString("conditioncode"), this.getName(), this.getLong("matusetransid"));
            }
            else if (!this.isNull("conditioncode") && invcost == null) {
                invcost = (InvCost)invMbo.addInvCostRecord(this.getString("conditioncode"));
                invcost.setValue("stdcost", this.getDouble("unitcost"), 2L);
            }
            if (invBalMbo == null) {
                if (invMbo.getOwner() == null) {
                    final MboRemote ownerOfThisMatUse = this.getOwner();
                    if (ownerOfThisMatUse != null && ownerOfThisMatUse instanceof InventoryRemote) {
                        final MboSetRemote locSet = this.getMboServer().getMboSet("LOCATIONS", this.getUserInfo());
                        final SqlFormat sqf2 = new SqlFormat(this.getUserInfo(), "location=:1 and siteid=:2");
                        sqf2.setObject(1, "LOCATIONS", "LOCATION", this.getString("storeloc"));
                        sqf2.setObject(2, "SITE", "SITEID", this.getString("siteid"));
                        locSet.setWhere(sqf2.format());
                        locSet.reset();
                        final MboRemote location2 = locSet.getMbo(0);
                        invMbo.getThisMboSet().setOwner(location2);
                    }
                    else {
                        invMbo.getThisMboSet().setOwner(this);
                    }
                }
                invBalMbo = (InvBalances)this.getInvBalancesRecord(invMbo);
                if (invBalMbo == null) {
                    invBalMbo = (InvBalances)invMbo.getMboSet("INVBALANCES").add();
                    invBalMbo.setValue("binnum", this.getString("binnum"), 11L);
                    invBalMbo.setValue("lotnum", this.getString("lotnum"), 11L);
                    invBalMbo.setValue("conditioncode", this.getString("conditioncode"), 11L);
                }
            }
        }
        if (invMbo != null && !useIntegration) {
            if (!this.sharedInvBalancesHasBeenUpdated || !this.sharedInventoryHasBeenUpdated) {
                if (!this.sharedInventoryHasBeenUpdated) {
                    this.updateInventory(invMbo, invBalMbo, invcost);
                    this.sharedInventoryHasBeenUpdated = true;
                }
                if (!this.sharedInvBalancesHasBeenUpdated) {
                    this.setValue("curbal", invBalMbo.getCurrentBalance(), 2L);
                    this.setValue("physcnt", invBalMbo.getPhysicalCount(), 2L);
                    this.updateInvBalances(invBalMbo);
                    this.sharedInvBalancesHasBeenUpdated = true;
                }
                this.setUncommitted(true);
                super.save();
                this.setFlag(7L, true);
            }
            this.setUncommitted(true);
            if (!this.isNull("newphyscnt")) {
                Date phyCntDate = this.getDate("NEWPHYSCNTDATE");
                if (this.isNull("NEWPHYSCNTDATE")) {
                    phyCntDate = this.getDate("ACTUALDATE");
                }
                final Calendar phyCal = Calendar.getInstance();
                phyCal.setTime(phyCntDate);
                final int second = phyCal.get(13);
                phyCal.set(13, second + 1);
                final Date datePlusOneSecond = phyCal.getTime();
                invMbo.adjustPhysicalCount(this.getString("binnum"), this.getString("lotnum"), this.getDouble("newphyscnt"), datePlusOneSecond, null, this.getString("conditioncode"));
            }
        }
        super.save();
        this.createInvoiceOnConsumption();
    }
    
    public void createInvoiceOnConsumption() throws MXException, RemoteException {
        final Inventory invMbo = (Inventory)this.getSharedInventory();
        if (this.toBeAdded() && invMbo != null && invMbo.isConsignment() && MXMath.abs(this.getDouble("quantity")) > 0.0) {
            final String invGenType = this.getTranslator().toInternalString("INVGENTYPE", invMbo.getString("invgentype"), this);
            if (invGenType != null && invGenType.equalsIgnoreCase("CONSUMPTION")) {
                final UserInfo userInfo = this.getUserInfo();
                final InvoiceServiceRemote invoiceService = (InvoiceServiceRemote)MXServer.getMXServer().lookup("INVOICE");
                final MboSetRemote consTransactionSet = invoiceService.addConsignmentTransactions(this, userInfo);
                if (!consTransactionSet.isEmpty()) {
                    final MboSetRemote invoiceSet = invoiceService.createInvoicesForConsignment(userInfo, consTransactionSet, invGenType);
                    final MboRemote invoice = invoiceSet.getMbo(0);
                    if (invoice != null) {
                        this.setValue("consinvoicenum", invoice.getString("invoicenum"), 11L);
                        invoiceSet.setMXTransaction(this.getMXTransaction());
                    }
                }
            }
        }
    }
    
    protected void doKitMakeInvBalanceUpdates() throws MXException, RemoteException {
        final Inventory fromInventoryMbo = (Inventory)this.getSharedInventoryForKit(this.getString("storeloc"), this.getString("siteid"), this.getString("itemnum"));
        final double quantity = MXMath.abs(this.getDouble("quantity"));
        final MboSetRemote invBalancesToBuildKit = fromInventoryMbo.getInvBalancesSetForKitComponent("");
        invBalancesToBuildKit.setOrderBy("curbal desc");
        MboRemote fromInvBal;
        int x;
        String fromInvDefaultBinnum;
        for (fromInvBal = null, x = 0, fromInvDefaultBinnum = fromInventoryMbo.getString("binnum"); (fromInvBal = invBalancesToBuildKit.getMbo(x)) != null && !fromInvBal.getString("binnum").equalsIgnoreCase(fromInvDefaultBinnum); ++x) {}
        x = 0;
        if (fromInvBal == null) {
            fromInvBal = invBalancesToBuildKit.getMbo(x);
        }
        double qtyFulfilled = 0.0;
        do {
            final double invBalCurBal = fromInvBal.getDouble("curbal");
            MboRemote matUseForNewBin = null;
            if (qtyFulfilled > 0.0) {
                matUseForNewBin = this.copy();
            }
            if (fromInvBal.getString("binnum").equalsIgnoreCase(fromInvDefaultBinnum) && fromInvBal.isSelected()) {
                ++x;
            }
            else {
                double qtyToTakeFromThisInvCurBal = 0.0;
                if (invBalCurBal >= quantity - qtyFulfilled) {
                    qtyToTakeFromThisInvCurBal = quantity - qtyFulfilled;
                }
                else {
                    qtyToTakeFromThisInvCurBal = invBalCurBal;
                }
                double fromOldBal = 0.0;
                fromOldBal = fromInvBal.getDouble("curbal");
                ((InvBalances)fromInvBal).updateCurrentBalance(qtyToTakeFromThisInvCurBal * -1.0 + fromOldBal);
                if (matUseForNewBin != null) {
                    matUseForNewBin.setValue("quantity", qtyToTakeFromThisInvCurBal * -1.0, 2L);
                    matUseForNewBin.setValue("binnum", fromInvBal.getString("binnum"), 2L);
                    matUseForNewBin.setValue("curbal", fromOldBal, 2L);
                    matUseForNewBin.setValue("physcnt", fromInvBal.getDouble("physcnt"), 2L);
                }
                else {
                    this.setValue("quantity", qtyToTakeFromThisInvCurBal * -1.0, 2L);
                    this.setValue("binnum", fromInvBal.getString("binnum"), 2L);
                    this.setValue("curbal", fromOldBal, 2L);
                    this.setValue("physcnt", fromInvBal.getDouble("physcnt"), 2L);
                }
                qtyFulfilled += qtyToTakeFromThisInvCurBal;
                if (fromInvBal.getString("binnum").equalsIgnoreCase(fromInvDefaultBinnum)) {
                    fromInvBal.select();
                }
                else {
                    ++x;
                }
            }
        } while ((fromInvBal = invBalancesToBuildKit.getMbo(x)) != null && qtyFulfilled < quantity);
    }
    
    private MboRemote getSharedInventoryForKit(final String storeLoc, final String siteId, final String itemnum) throws MXException, RemoteException {
        final MboRemote owner = this.getOwner();
        if (owner instanceof InventoryRemote && owner.getString("location").equals(storeLoc) && owner.getString("itemnum").equals(itemnum)) {
            return owner;
        }
        final SqlFormat sqf = new SqlFormat(this, "itemnum = :1 and location = :2 and siteid = :3");
        sqf.setObject(1, "INVENTORY", "itemnum", itemnum);
        sqf.setObject(2, "INVENTORY", "location", storeLoc);
        sqf.setObject(3, "INVENTORY", "siteid", siteId);
        final MboSetRemote invSet = ((MboSet)this.getThisMboSet()).getSharedMboSet("INVENTORY", sqf.format());
        if (invSet != null) {
            invSet.setOwner(this);
        }
        if (invSet == null || invSet.isEmpty()) {
            return null;
        }
        return invSet.getMbo(0);
    }
    
    private void updateInventory(final Inventory invMbo, final InvBalances invBal, final InvCost invcost) throws MXException, RemoteException {
        if (invMbo.toBeAdded()) {
            invMbo.updateInventoryIssueDetails(this.getDate("actualdate"), this.getDouble("quantity"));
        }
        else {
            final MatUseTransSet owningSet = (MatUseTransSet)this.getThisMboSet();
            owningSet.addDeltaIssueYTD(invMbo, this.getDouble("quantity"), this);
        }
        if (this.isReturn() && invcost != null) {
            ///AMB===v===
            /// Error #3
            /// No debe modificarse el PPP en una devolución de consumo
            /*
            invMbo.updateInventoryAverageCost(this.getDouble("quantity"), Math.abs(this.getDouble("linecost")), 1.0, invcost);
            */
		    ///AMB===^===
            invcost.increaseAccumulativeReceiptQty(this.getDouble("quantity"));
        }
    }
    
    @Override
    public MboRemote getSharedInventory() throws MXException, RemoteException {
        final MboRemote parent = this.getOwner();
        if (parent != null && parent.getName().equals("INVENTORY")) {
            return parent;
        }
        final SqlFormat sqf = new SqlFormat(this, "itemnum = :itemnum and itemsetid = :itemsetid and location = :storeloc and siteid=:siteid");
        final MboSetRemote invSet = ((MboSet)this.getThisMboSet()).getSharedMboSet("INVENTORY", sqf.format());
        if (invSet == null || invSet.isEmpty()) {
            return null;
        }
        return invSet.getMbo(0);
    }
    
    public MboSetRemote getSharedAssetSet() throws MXException, RemoteException {
        final MboRemote owner = this.getOwner();
        MboSetRemote assetSet = null;
        if (this.isReturn()) {
            final SqlFormat sqf = new SqlFormat(this, "assetnum = :rotassetnum and siteid=:tositeid");
            assetSet = ((MboSet)this.getThisMboSet()).getSharedMboSet("ASSET", sqf.format());
        }
        else if (owner != null && owner.isBasedOn("INVUSE")) {
            assetSet = this.getMboSet("ROTASSET");
        }
        else {
            final SqlFormat sqf = new SqlFormat(this, "assetnum = :rotassetnum and siteid=:siteid");
            assetSet = ((MboSet)this.getThisMboSet()).getSharedMboSet("ASSET", sqf.format());
        }
        if (assetSet == null || assetSet.isEmpty()) {
            return null;
        }
        if (assetSet.getOwner() == null) {
            assetSet.setOwner(this);
        }
        return assetSet;
    }
    
    @Override
    public MboRemote getSharedInvBalance() throws MXException, RemoteException {
        final MboRemote owner = this.getOwner();
        InvBalances bal = null;
        final String checkBinnum = this.getString("binnum");
        final String checkLotnum = this.getString("lotnum");
        final String conditionCode = this.getString("conditionCode");
        if (owner != null && owner.getName().equals("INVENTORY")) {
            bal = ((Inventory)owner).getInvBalanceRecord(checkBinnum, checkLotnum, conditionCode);
        }
        else {
            final MboRemote inventoryMbo = this.getSharedInventory();
            if (inventoryMbo == null) {
                return null;
            }
            bal = ((Inventory)inventoryMbo).getInvBalanceRecord(checkBinnum, checkLotnum, conditionCode);
        }
        return bal;
    }
    
    @Override
    public MboRemote getInvBalancesRecord(final MboRemote inventory) throws MXException, RemoteException {
        final MboSetRemote invBalSet = inventory.getMboSet("INVBALANCES");
        int i = 0;
        MboRemote invBal = null;
        while ((invBal = invBalSet.getMbo(i)) != null) {
            if (invBal.getString("itemnum").equals(this.getString("itemnum")) && invBal.getString("location").equals(this.getString("storeloc")) && invBal.getString("itemsetid").equals(this.getString("itemsetid")) && invBal.getString("binnum").equals(this.getString("binnum")) && invBal.getString("lotnum").equals(this.getString("lotnum")) && invBal.getString("conditioncode").equals(this.getString("conditioncode")) && invBal.getString("siteid").equals(this.getString("siteid"))) {
                return invBal;
            }
            ++i;
        }
        return null;
    }
    
    private MboRemote getInvCostRecord(final MboRemote inventory) throws MXException, RemoteException {
        final MboSetRemote invcostSet = inventory.getMboSet("INVCOST");
        int i = 0;
        MboRemote invCost = null;
        while ((invCost = invcostSet.getMbo(i)) != null) {
            if (invCost.getString("itemnum").equals(this.getString("itemnum")) && invCost.getString("location").equals(this.getString("storeloc")) && invCost.getString("itemsetid").equals(this.getString("itemsetid")) && invCost.getString("conditioncode").equals(this.getString("conditioncode")) && invCost.getString("siteid").equals(this.getString("siteid"))) {
                return invCost;
            }
            ++i;
        }
        return null;
    }
    
    private boolean isRotAssetReturnedToStore() throws MXException, RemoteException {
        return this.isReturn() && !this.isNull("RotAssetNum") && !this.isNull("storeloc");
    }
    
    private void updateInvBalances(final InvBalances invBal) throws MXException, RemoteException {
        if (invBal.toBeAdded()) {
            final double newValue = invBal.getDouble("curbal") + this.getDouble("quantity");
            invBal.setValue("curbal", newValue, 2L);
        }
        else {
            final MatUseTransSet owningSet = (MatUseTransSet)this.getThisMboSet();
            owningSet.addDeltaCurbal(invBal, this.getDouble("quantity"), this);
        }
    }
    
    @Override
    public void setIgnoreLocationAssetMismatch(final boolean value) {
        this.ignoreAssetLocMismatch = value;
    }
    
    @Override
    public void setIgnoreLocationOccupied(final boolean value) {
        this.ignoreLocOccupied = value;
    }
    
    @Override
    public void setUpdateWorkOrdersOnMoveAsset(final boolean value) {
        this.updateWO = value;
    }
    
    private void moveAsset() throws MXException, RemoteException {
        final MboSetRemote rotAssetRecSet = this.getSharedAssetSet();
        this.rotAssetRec = (AssetRemote)rotAssetRecSet.getMbo(0);
        if (this.rotAssetRec == null) {
            return;
        }
        final Date moveDate = this.getDate("actualdate");
        this.rotAssetRec.setValue("movedby", this.getString("enterby"), 11L);
        String moveToLoc = "";
        if (this.isReturn()) {
            moveToLoc = this.getString("storeloc");
            this.rotAssetRec.setValue("newsite", this.getString("siteid"), 11L);
            this.rotAssetRec.setValue("newlocation", moveToLoc, 11L);
            if (this.getString("siteid").equalsIgnoreCase(this.getString("tositeid"))) {
                this.rotAssetRec.setValue("binnum", this.getString("binnum"), 11L);
            }
        }
        else {
            moveToLoc = this.getString("location");
            this.rotAssetRec.setValue("newsite", this.getString("tositeid"), 11L);
            this.rotAssetRec.setValue("newlocation", moveToLoc, 11L);
        }
        if (!this.isNull("itemnum") && !this.isReturn() && !this.rotAssetRec.getBoolean("mainthierchy")) {
            final MboRemote item = this.getMboSet("ITEM").getMbo(0);
            if (item.getBoolean("attachonissue")) {
                String parent = null;
                if (this.isNull("assetnum")) {
                    if (!this.isNull("wonum")) {
                        final MboRemote WO = this.getMboSet("WORKORDER").getMbo(0);
                        if (!WO.isNull("assetnum")) {
                            parent = WO.getString("assetnum");
                        }
                    }
                }
                else {
                    parent = this.getString("assetnum");
                }
                if (parent != null) {
                    try {
                        this.rotAssetRec.setValue("newparent", parent, 2L);
                    }
                    catch (Exception e) {
                        this.rotAssetRec.setValue("newparent", this.rotAssetRec.getString("parent"), 11L);
                    }
                }
            }
        }
        this.rotAssetRec.issueAsset(moveToLoc, this.getString("memo"), moveDate, this.getString("wonum"), !this.ignoreAssetLocMismatch, !this.ignoreLocOccupied, this.updateWO, this.getString("matusetransid"));
        if (rotAssetRecSet.hasWarnings()) {
            final MboSet thisSet = (MboSet)this.getThisMboSet();
            thisSet.clearWarnings();
            final MXException[] a = rotAssetRecSet.getWarnings();
            for (int i = 0; i < a.length; ++i) {
                thisSet.addWarning(a[i]);
            }
        }
    }
    
    private void updateWorkOrder() throws MXException, RemoteException {
        if (this.isNull("RefWO")) {
            return;
        }
        MboRemote wombo = null;
        final MboRemote owner = this.getOwner();
        if (owner != null && owner instanceof WORemote) {
            return;
        }
        MboRemote ownersOwner = null;
        if (owner != null) {
            ownersOwner = owner.getOwner();
        }
        if (owner != null && owner instanceof InventoryRemote && ownersOwner != null && ownersOwner instanceof WORemote && wombo != null && wombo.getString("wonum").equalsIgnoreCase(this.getString("wonum"))) {
            wombo = ownersOwner;
        }
        if (wombo == null) {
            final SqlFormat sqf = new SqlFormat(this, "wonum=:RefWO and siteid=:tositeid");
            final MboSetRemote woset = ((MboSet)this.getThisMboSet()).getSharedMboSet("WORKORDER", sqf.format());
            wombo = woset.getMbo(0);
        }
        if (wombo != null) {
            final IntegrationServiceRemote intserv = (IntegrationServiceRemote)((AppService)this.getMboServer()).getMXServer().lookup("INTEGRATION");
            final boolean useIntegration = intserv.useIntegrationRules(this.getString("ownersysid"), wombo.getString("ownersysid"), "INVISSWO", this.getUserInfo());
            if (!useIntegration && !this.workOrderUpdated) {
                if (this.isReturn()) {
                    ((WORemote)wombo).incrActMatCost(Math.abs(this.getDouble("linecost")) * -1.0, this.getBoolean("outside"));
                }
                else {
                    ((WORemote)wombo).incrActMatCost(Math.abs(this.getDouble("linecost")), this.getBoolean("outside"));
                }
                this.setWorkOrderUpdatedFlag(true);
                if (owner != null && owner.isBasedOn("InvUse")) {
                    ((WOSet)wombo.getThisMboSet()).skipappvalidate = true;
                }
            }
        }
    }
    
    private void updateInvReserve() throws MXException, RemoteException {
        if (this.isReturn()) {
            return;
        }
        final MboRemote owner = this.getOwner();
        if (owner != null && owner instanceof InvReserveRemote) {
            ((InvReserveRemote)owner).incrActualQty(Math.abs(this.getDouble("quantity")));
            return;
        }
        final boolean useMR = !this.getString("mrnum").equals("") && !this.getString("mrlinenum").equals("");
        final MboSetRemote invrSet = this.getSharedInvReserveSet();
        if (invrSet.isEmpty()) {
            invrSet.reset();
            return;
        }
        if (useMR) {
            int x = 0;
            while (true) {
                InvReserveRemote invrMbo = (InvReserveRemote)invrSet.getMbo(x);
                if (invrMbo == null) {
                    break;
                }
                if (invrMbo.getString("mrnum").equals(this.getString("mrnum")) && invrMbo.getString("mrlinenum").equals(this.getString("mrlinenum"))) {
                    final MatUseTransSet matUseSet = (MatUseTransSet)this.getThisMboSet();
                    final Vector<InvReserveRemote> invResVector = (Vector<InvReserveRemote>)matUseSet.getInvReserveVector();
                    final InvReserveRemote invResInVector = this.getInvReserveInVector(invResVector, invrMbo);
                    if (invResInVector != null) {
                        invrMbo = invResInVector;
                    }
                    else {
                        invResVector.addElement(invrMbo);
                    }
                    invrMbo.incrActualQty(Math.abs(this.getDouble("quantity")));
                    this.setInvReserveUpdatedFlag(true);
                    break;
                }
                ++x;
            }
        }
        else {
            InvReserveRemote invrMbo2 = (InvReserveRemote)invrSet.getMbo(0);
            final MatUseTransSet matUseSet2 = (MatUseTransSet)this.getThisMboSet();
            final Vector<InvReserveRemote> invResVector2 = (Vector<InvReserveRemote>)matUseSet2.getInvReserveVector();
            final InvReserveRemote invResInVector2 = this.getInvReserveInVector(invResVector2, invrMbo2);
            if (invResInVector2 != null) {
                invrMbo2 = invResInVector2;
            }
            else {
                invResVector2.addElement(invrMbo2);
            }
            invrMbo2.incrActualQty(Math.abs(this.getDouble("quantity")));
            this.setInvReserveUpdatedFlag(true);
        }
    }
    
    public MboSetRemote getSharedInvReserveSet() throws MXException, RemoteException {
        SqlFormat sqf = null;
        final String requestNum = this.getString("requestnum");
        if (!this.getString("mrnum").equals("") && !this.getString("mrlinenum").equals("")) {
            final StringBuffer mrLineNum = new StringBuffer("(");
            final StringBuffer mrNum = new StringBuffer("(");
            final MboSetRemote thisMboSet = this.getThisMboSet();
            int x = 0;
            while (true) {
                final MboRemote thisMbo = thisMboSet.getMbo(x);
                if (thisMbo == null) {
                    break;
                }
                if (!thisMbo.getString("mrnum").equals("") || !thisMbo.getString("mrlinenum").equals("")) {
                    mrNum.append("'");
                    mrLineNum.append(thisMbo.getInt("mrlinenum"));
                    mrNum.append(thisMbo.getString("mrnum"));
                    mrLineNum.append(",");
                    mrNum.append("',");
                }
                ++x;
            }
            String mrString = mrNum.toString();
            String mrLineString = mrLineNum.toString();
            if (mrString.charAt(mrString.length() - 1) == ',') {
                mrString = mrString.substring(0, mrString.length() - 1) + ")";
            }
            if (mrLineString.charAt(mrLineString.length() - 1) == ',') {
                mrLineString = mrLineString.substring(0, mrLineString.length() - 1) + ")";
            }
            sqf = new SqlFormat(this, "mrnum in " + mrString + " and mrlinenum in " + mrLineString);
        }
        else if (!this.isNull("itemnum") && (requestNum == null || requestNum.equals(""))) {
            final StringBuffer where = new StringBuffer("");
            if (!this.isNull("mrnum")) {
                where.append(" and mrnum = :mrnum");
                if (this.isNull("mrlinenum")) {
                    where.append(" and mrlinenum is null");
                }
                else {
                    where.append(" and mrlinenum = :mrlinenum");
                }
            }
            else if (!this.isNull("ponum")) {
                where.append(" and ponum=:ponum");
                if (this.isNull("polinenum")) {
                    where.append(" and polinenum is null");
                }
                else {
                    where.append(" and polinenum=:polinenum");
                }
            }
            else if (!this.isNull("refwo")) {
                where.append(" and wonum = :refwo");
                if (!this.isNull("assetnum")) {
                    where.append(" and assetnum = :assetnum");
                }
            }
            else if (!this.isNull("assetnum")) {
                where.append(" and assetnum = :assetnum and wonum is null");
            }
            if (where.toString().equals("")) {
                where.append(" and 1=2 ");
            }
            sqf = new SqlFormat(this, "itemnum=:itemnum and location=:storeloc and storelocsiteid=:tositeid" + where.toString());
        }
        else {
            sqf = new SqlFormat(this, "requestnum = :1");
            sqf.setObject(1, "INVRESERVE", "REQUESTNUM", requestNum);
        }
        final String whereClause = sqf.format();
        final MboSetRemote invrSet = ((MboSet)this.getThisMboSet()).getSharedMboSet("INVRESERVE", whereClause);
        return invrSet;
    }
    
    private void updateAssetSparePart() throws MXException, RemoteException {
        final ItemRemote itemMbo = (ItemRemote)this.getMboSet("ITEM").getMbo(0);
        if (itemMbo == null) {
            return;
        }
        final String assetnum = this.getString("assetnum");
        final String siteid = this.getString("tositeid");
        final String itemnum = this.getString("itemnum");
        final String itemsetid = this.getString("itemsetid");
        if (!itemMbo.canSparePartAutoAdd() && !itemMbo.sparePartExists(assetnum, siteid)) {
            return;
        }
        final MatUseTransSet matUseSet = (MatUseTransSet)this.getThisMboSet();
        if (matUseSet.sparePartExistsInHashTable(assetnum + siteid, itemnum)) {
            return;
        }
        if (itemMbo.sparePartExists(assetnum, siteid)) {
            final MboRemote sparePart = this.getSparePart(itemnum, itemsetid, assetnum, siteid);
            if (sparePart != null) {
                final double currentIssuedQty = sparePart.getDouble("issuedqty");
                final double matUseIssuedQty = this.getDouble("quantity");
                double newIssuedQty = currentIssuedQty - matUseIssuedQty;
                if (newIssuedQty < 0.0) {
                    newIssuedQty = 0.0;
                }
                sparePart.setValue("issuedqty", newIssuedQty, 11L);
            }
            return;
        }
        itemMbo.addSparePart(assetnum, siteid);
        matUseSet.storeNewSparePartKeys(assetnum + siteid, itemnum);
        this.setValue("sparepartadded", true, 2L);
    }
    
    private void handleMeterReadingOnIssue() throws MXException, RemoteException {
        if (this.toBeAdded() && this.isIssue()) {
            final ItemRemote item = (ItemRemote)this.getMboSet("ITEM").getMbo(0);
            if (this.isNull("rotassetnum") && !item.isNull("metername")) {
                DeployedMeterRemote dmr = null;
                boolean meterreadingEntered = false;
                if (!this.isNull("ASSETNUM")) {
                    final SqlFormat assetmeter = new SqlFormat(this, "assetnum = :assetnum and metername = :1 and orgid = :orgid and siteid=:tositeid");
                    assetmeter.setObject(1, "ASSETMETER", "METERNAME", item.getString("metername"));
                    dmr = (DeployedMeterRemote)this.getMboSet("$assetMeterForItem", "ASSETMETER", assetmeter.format()).getMbo(0);
                    if (dmr != null) {
                        meterreadingEntered = dmr.enterReadingOnMaterialIssue();
                    }
                }
                if (!this.isNull("LOCATION") && !meterreadingEntered) {
                    final SqlFormat locationmeter = new SqlFormat(this, "location = :location and metername = :1 and orgid = :orgid and siteid=:tositeid");
                    locationmeter.setObject(1, "LOCATIONMETER", "METERNAME", item.getString("metername"));
                    dmr = (DeployedMeterRemote)this.getMboSet("$locationMeterForItem", "LOCATIONMETER", locationmeter.format()).getMbo(0);
                    if (dmr != null) {
                        dmr.enterReadingOnMaterialIssue();
                    }
                }
            }
        }
    }
    
    @Override
    public boolean canBeReturned() throws MXException, RemoteException {
        if (!this.isNull("rotassetnum")) {
            final MboSetRemote rotAssetSet = this.getMboSet("ROTASSET");
            if (rotAssetSet.isEmpty()) {
                rotAssetSet.reset();
                return false;
            }
            final String curLocation = rotAssetSet.getMbo(0).getString("location");
            if (curLocation.equals(this.getString("location"))) {
                rotAssetSet.reset();
                return true;
            }
        }
        return true;
    }
    
    @Override
    public MboRemote returnIssue(final String bin, final String lot, final double qty) throws MXException, RemoteException {
        final MboRemote matReturn = this.returnIssue();
        matReturn.setValue("quantity", qty, 2L);
        matReturn.setValue("binnum", bin, 2L);
        matReturn.setValue("lotnum", lot, 2L);
        return matReturn;
    }
    
    @Override
    public MboRemote returnIssue() throws MXException, RemoteException {
        return this.returnIssue(this.getThisMboSet());
    }
    
    @Override
    public MboRemote returnIssue(final MboSetRemote newMatUseSet) throws MXException, RemoteException {
        if (this.isReturn()) {
            throw new MXApplicationException("inventory", "returnOnlyIssues");
        }
        final IntegrationServiceRemote intserv = (IntegrationServiceRemote)((AppService)this.getMboServer()).getMXServer().lookup("INTEGRATION");
        final InventoryRemote invMbo = (InventoryRemote)this.getSharedInventory();
        if (invMbo == null) {
            throw new MXApplicationException("inventory", "invbalNotInInventory");
        }
        final boolean useIntegration = intserv.useIntegrationRules(this.getString("ownersysid"), invMbo.getString("ownersysid"), "INVISSR", this.getUserInfo());
        if (useIntegration) {
            throw new MXApplicationException("inventory", "mxcollabINVISSR");
        }
        if (!this.canBeReturned()) {
            throw new MXApplicationException("inventory", "cannotReturnIssue");
        }
        if (Math.abs(this.remainQty + 1.11) < 1.0E-7) {
            this.totalQty = this.getTotalQtyForReturn();
            this.remainQty = this.totalQty;
        }
        if (this.remainQty <= 0.0) {
            throw new MXApplicationException("inventory", "cannotreturnanymore");
        }
        final MatUseTransRemote rtn = (MatUseTransRemote)newMatUseSet.add();
        try {
            rtn.setIssueForThisReturn(this);
            rtn.setTotalQtyForThisReturn(this.totalQty);
            rtn.setValue("issueid", this.getLong("matusetransid"), 2L);
            rtn.setValue("ISSUETYPE", this.getTranslator().toExternalDefaultValue("ISSUETYP", "RETURN", this), 11L);
            rtn.setValue("POSITIVEQUANTITY", this.remainQty, 11L);
            rtn.setValue("QUANTITY", this.remainQty, 2L);
            this.remainQty = 0.0;
            ((MatUseTrans)rtn).selectItemForReturn = true;
            rtn.setValue("ITEMNUM", this.getString("ITEMNUM"), 2L);
            rtn.setValue("itemsetid", this.getString("itemsetid"), 2L);
            rtn.setValue("LOCATION", this.getString("LOCATION"), 11L);
            rtn.setValue("ROTASSETNUM", this.getString("ROTASSETNUM"), 3L);
            rtn.setValue("tositeid", this.getString("tositeid"), 11L);
            rtn.setValue("conditioncode", this.getString("conditioncode"), 11L);
            rtn.setValue("condrate", this.getString("condrate"), 11L);
            rtn.setValue("STORELOC", this.getString("STORELOC"), 11L);
            rtn.setValue("BINNUM", this.getString("BINNUM"), 11L);
            rtn.setValue("LOTNUM", this.getString("LOTNUM"), 11L);
            rtn.setValue("PONUM", this.getString("PONUM"), 11L);
            rtn.setValue("CONVERSION", this.getDouble("CONVERSION"), 2L);
            rtn.setValue("MRNUM", this.getString("MRNUM"), 11L);
            rtn.setValue("MRLINENUM", this.getString("MRLINENUM"), 11L);
            rtn.setValue("WONUM", this.getString("WONUM"), 2L);
            rtn.setValue("FINCNTRLID", this.getString("FINCNTRLID"), 11L);
            rtn.setValue("ASSETNUM", this.getString("ASSETNUM"), 11L);
            rtn.setValue("OUTSIDE", this.getString("OUTSIDE"), 11L);
            rtn.setValue("TASKID", this.getString("TASKID"), 11L);
            rtn.setValue("REFWO", this.getString("REFWO"), 11L);
            rtn.setValue("PACKINGSLIPNUM", this.getString("PACKINGSLIPNUM"), 11L);
            rtn.setValue("POLINENUM", this.getString("POLINENUM"), 11L);
            rtn.setValue("DESCRIPTION", this.getString("DESCRIPTION"), 11L);
            rtn.setValue("CURRENCYCODE", this.getString("CURRENCYCODE"), 2L);
            if (!this.isNull("CURRENCYUNITCOST")) {
                rtn.setValue("CURRENCYUNITCOST", this.getDouble("CURRENCYUNITCOST"), 11L);
            }
            rtn.setValue("SPAREPARTADDED", this.getString("SPAREPARTADDED"), 11L);
            rtn.setValue("QTYREQUESTED", this.getString("QTYREQUESTED"), 11L);
            rtn.setValue("GLDEBITACCT", this.getString("GLDEBITACCT"), 11L);
            rtn.setValue("GLCREDITACCT", this.getString("GLCREDITACCT"), 11L);
            rtn.setValue("UNITCOST", this.getString("UNITCOST"), 2L);
            rtn.setValue("ACTUALCOST", this.getString("ACTUALCOST"), 11L);
            rtn.setValue("MEMO", this.getString("MEMO"), 11L);
            rtn.setValue("IT1", this.getString("IT1"), 3L);
            rtn.setValue("IT2", this.getString("IT2"), 3L);
            rtn.setValue("IT3", this.getString("IT3"), 3L);
            rtn.setValue("IT4", this.getString("IT4"), 3L);
            rtn.setValue("IT5", this.getString("IT5"), 3L);
            rtn.setValue("IT6", this.getString("IT6"), 3L);
            rtn.setValue("IT7", this.getString("IT7"), 3L);
            rtn.setValue("IT8", this.getString("IT8"), 3L);
            rtn.setValue("IT9", this.getString("IT9"), 3L);
            rtn.setValue("IT10", this.getString("IT10"), 3L);
            rtn.setValue("ISSUETO", this.getString("ISSUETO"), 11L);
            final String[] edits = { "POSITIVEQUANTITY", "BINNUM", "UNITCOST", "CURRENCYUNITCOST", "ACTUALDATE", "MEMO", "ISSUETYPE" };
            ((MatUseTrans)rtn).selectItemForReturn = true;
            final MboSetInfo msi = this.getMboSetInfo();
            final Map attrsOrig = msi.getAttributeDetails();
            for (final MboValueInfo mvi : attrsOrig.values()) {
                if (!mvi.isUserdefined()) {
                    if (Arrays.asList(edits).contains(mvi.getAttributeName())) {
                        continue;
                    }
                    rtn.setFieldFlag(mvi.getAttributeName().toLowerCase(), 7L, true);
                }
                else {
                    final String attrName = mvi.getName();
                    if (attrName == null || attrName.equals("")) {
                        continue;
                    }
                    rtn.setValue(attrName, this.getString(attrName), 66L);
                }
            }
        }
        catch (MXException m) {
            rtn.delete();
            rtn.getThisMboSet().remove();
            this.setValue("QTYRETURNED", this.getMboValue("QTYRETURNED").getPreviousValue().asDouble(), 2L);
            this.setValueNull("ISSUEID", 2L);
            throw m;
        }
        catch (RemoteException r) {
            rtn.delete();
            rtn.getThisMboSet().remove();
            this.setValue("QTYRETURNED", this.getMboValue("QTYRETURNED").getPreviousValue().asDouble(), 2L);
            this.setValueNull("ISSUEID", 2L);
            throw r;
        }
        return rtn;
    }
    
    @Override
    public void setIssueForThisReturn(final MboRemote issue) throws MXException, RemoteException {
        this.issueMbo = issue;
    }
    
    @Override
    public void setRotatingAssetnum(final String rotAssetnum) throws MXException, RemoteException {
        if (!((ItemRemote)this.getMboSet("ITEM").getMbo(0)).isRotating()) {
            throw new MXApplicationException("inventory", "matusetransItemNotRotating");
        }
        this.setValue("rotassetnum", rotAssetnum, 2L);
    }
    
    void updateGlAccounts() throws MXException, RemoteException {
        final TransactionGLMerger glm = new TransactionGLMerger(this);
        glm.setMRInfo(this.getString("mrnum"), this.getString("mrlinenum"));
        glm.setItem(this.getString("itemnum"));
        glm.setItemSetID(this.getString("itemsetid"));
        glm.setConditionCode(this.getString("conditioncode"));
        final MboRemote owner = this.getOwner();
        if (owner == null || !(owner instanceof WORemote) || !this.getString("wonum").equals(owner.getString("wonum"))) {
            glm.setWonum(this.getString("wonum"));
        }
        else {
            glm.setWO(owner);
        }
        glm.setAssetnum(this.getString("assetnum"));
        glm.setLocation(this.getString("location"));
        glm.setStoreLoc(this.getString("storeloc"));
        if (!this.isNull("gldebitacct")) {
            this.setValue("gldebitacct", glm.mergedGL(this.getTopGL(glm), this.getString("gldebitacct")), 11L);
        }
        else {
            this.setValue("gldebitacct", glm.getMergedDebitGLAccount(), 11L);
        }
    }
    
    private String getTopGL(final TransactionGLMerger glm) throws MXException, RemoteException {
        String topGl = null;
        if (!this.isNull("itemnum")) {
            if (this.isNull("storeloc")) {
                topGl = glm.getItemResourceAccount();
            }
            else {
                topGl = glm.getInventoryGLAccount();
            }
        }
        return topGl;
    }
    
    @Override
    public boolean isReturn() throws MXException, RemoteException {
        return !this.isNull("ISSUETYPE") && this.getTranslator().toInternalString("ISSUETYP", this.getString("ISSUETYPE")).equalsIgnoreCase("RETURN");
    }
    
    @Override
    public boolean isIssue() throws MXException, RemoteException {
        return !this.isNull("ISSUETYPE") && this.getTranslator().toInternalString("ISSUETYP", this.getString("ISSUETYPE")).equalsIgnoreCase("ISSUE");
    }
    
    protected boolean isKitMake() throws MXException, RemoteException {
        final Translate tr = this.getTranslator();
        return !this.isNull("ISSUETYPE") && tr.toInternalString("ISSUETYP", this.getString("issuetype")).equalsIgnoreCase("KITMAKE");
    }
    
    protected boolean isKitBreak() throws MXException, RemoteException {
        final Translate tr = this.getTranslator();
        return !this.isNull("ISSUETYPE") && tr.toInternalString("ISSUETYP", this.getString("issuetype")).equalsIgnoreCase("KITBREAK");
    }
    
    protected boolean isKitting() throws MXException, RemoteException {
        return !this.isNull("ISSUETYPE") && (this.isKitMake() || this.isKitBreak());
    }
    
    @Override
    public String[] getValidateOrder() {
        return new String[] { "UnitCost", "ActualDate", "ItemNum", "RotAssetnum", "Storeloc", "WoNum", "Location", "Assetnum", "Quantity" };
    }
    
    boolean hasIssueTypeChanged() throws MXException, RemoteException {
        return this.issueTypeHasChanged;
    }
    
    void setIssueTypeChange(final boolean changed) throws MXException, RemoteException {
        this.issueTypeHasChanged = changed;
    }
    
    @Override
    public void delete(final long accessModifier) throws MXException, RemoteException {
        super.delete(accessModifier);
        final MboRemote owner = this.getOwner();
        if (owner != null && owner instanceof WORemote) {
            final WORemote refWO = ((WORemote)owner).getWoFromCombined(this.getMboValue("RefWO").getString());
            if (this.isReturn()) {
                refWO.incrActMatCost(Math.abs(this.getDouble("LineCost")), this.getBoolean("Outside"));
            }
            else {
                refWO.incrActMatCost(-1.0 * this.getDouble("LineCost"), this.getBoolean("Outside"));
            }
        }
        final MboRemote inv = this.getSharedInventory();
        if (inv != null) {
            inv.getThisMboSet().reset();
        }
        final MboRemote invBal = this.getSharedInvBalance();
        if (invBal != null) {
            invBal.getThisMboSet().reset();
        }
        if (owner != null && owner instanceof LocationRemote && this.isReturn() && this.issueMbo != null) {
            this.returnQtyDeleted = this.issueMbo.getDouble("QTYRETURNED");
            this.issueMbo.setValue("QTYRETURNED", this.issueMbo.getMboInitialValue("QTYRETURNED").asDouble(), 11L);
        }
    }
    
    @Override
    public void undelete() throws MXException, RemoteException {
        final MboRemote owner = this.getOwner();
        if (owner != null && owner instanceof WORemote) {
            final WORemote refWO = ((WORemote)owner).getWoFromCombined(this.getMboValue("RefWO").getString());
            if (this.isReturn()) {
                refWO.incrActMatCost(-1.0 * this.getDouble("LineCost"), this.getBoolean("Outside"));
            }
            else {
                refWO.incrActMatCost(this.getDouble("LineCost"), this.getBoolean("Outside"));
            }
        }
        if (owner != null && owner instanceof LocationRemote && this.isReturn() && this.issueMbo != null) {
            this.issueMbo.setValue("QTYRETURNED", this.returnQtyDeleted, 11L);
        }
        super.undelete();
    }
    
    double getDefaultIssueCost() throws MXException, RemoteException {
        double issueCost = 0.0;
        if (!this.isNull("itemnum") && !this.isNull("storeloc")) {
            final String conditionCode = this.getString("conditioncode");
            final Inventory inventory = (Inventory)this.getMboSet("INVENTORY").getMbo(0);
            if (inventory != null) {
                final MboRemote invcost = inventory.getInvCostRecord(conditionCode);
                if (this.isIssue() || invcost != null) {
                    if (!this.isNull("rotassetnum")) {
                        final AssetRemote rotAsset = (AssetRemote)this.getMboSet("ROTATINGASSET").getMbo(0);
                        issueCost = inventory.getDefaultIssueCost(rotAsset, conditionCode);
                    }
                    else {
                        issueCost = inventory.getDefaultIssueCost(conditionCode);
                    }
                }
                else {
                    final double fullIssueCost = inventory.getDefaultIssueCost();
                    final MboRemote itemcond = this.getMboSet("ITEMCONDITION").getMbo(0);
                    final double condrate = itemcond.getDouble("condrate");
                    issueCost = fullIssueCost * (condrate / 100.0);
                    this.setValue("condrate", condrate, 11L);
                }
            }
        }
        return issueCost;
    }
    
    private void checkForNegativeBalance() throws MXException, RemoteException {
        final MboRemote owner = this.getOwner();
        double sumQuantity = 0.0;
        if (this.toBeAdded() && this.isIssue() && owner != null && !(owner instanceof MatRecTransRemote)) {
            final MboRemote inventory = this.getSharedInventory();
            if (inventory == null) {
                return;
            }
            final MboRemote invBal = this.getSharedInvBalance();
            if (invBal == null) {
                return;
            }
            final IntegrationServiceRemote intserv = (IntegrationServiceRemote)((AppService)this.getMboServer()).getMXServer().lookup("INTEGRATION");
            final boolean useIntegration = intserv.useIntegrationRules(this.getString("ownersysid"), inventory.getString("ownersysid"), "INV", this.getUserInfo());
            if (useIntegration) {
                return;
            }
            final InventoryService intServ = (InventoryService)((AppService)this.getMboServer()).getMXServer().lookup("INVENTORY");
            double avblQty = inventory.getDouble("avblBalance");
            if (avblQty < Math.abs(this.getDouble("quantity"))) {
                if (!this.isNull("requestnum")) {
                    final double reservedQty = this.getDouble("RESERVEDQTY");
                    final double curBal = invBal.getDouble("curbal");
                    if (curBal >= reservedQty || curBal >= Math.abs(this.getDouble("quantity"))) {
                        avblQty = curBal;
                    }
                }
                else {
                    final MboSetRemote invresSet = this.getSharedInvReserveSet();
                    if (!invresSet.isEmpty()) {
                        final double sumReservedQty = invresSet.sum("reservedqty");
                        avblQty += sumReservedQty;
                    }
                }
            }
            final MboSetRemote matUseTransSet = this.getThisMboSet();
            int i = 0;
            while (true) {
                final MatUseTrans matUseTrans = (MatUseTrans)matUseTransSet.getMbo(i);
                if (matUseTrans == null) {
                    break;
                }
                if (matUseTrans.toBeAdded() && !matUseTrans.toBeDeleted()) {
                    if (matUseTrans.isIssue()) {
                        if (this.getString("itemnum").equals(matUseTrans.getString("itemnum")) && this.getString("binnum").equals(matUseTrans.getString("binnum")) && this.getString("lotnum").equals(matUseTrans.getString("lotnum"))) {
                            sumQuantity += Math.abs(matUseTrans.getDouble("quantity"));
                        }
                    }
                }
                ++i;
            }
            intServ.canGoNegative(this.getUserInfo(), sumQuantity, invBal.getDouble("curbal"), avblQty, this);
        }
    }
    
    private MboRemote getSparePart(final String itemnum, final String itemsetid, final String assetnum, final String siteid) throws MXException, RemoteException {
        final MboRemote sparePartMbo = null;
        final SqlFormat sqf = new SqlFormat(this, "itemnum = :1 and assetnum = :2 and itemsetid = :3 and siteid =:4");
        sqf.setObject(1, "SPAREPART", "itemnum", itemnum);
        sqf.setObject(2, "SPAREPART", "assetnum", assetnum);
        sqf.setObject(3, "SPAREPART", "itemsetid", itemsetid);
        sqf.setObject(4, "SPAREPART", "siteid", siteid);
        final MboSetRemote spSet = ((MboSet)this.getThisMboSet()).getSharedMboSet("SPAREPART", sqf.format());
        if (!spSet.isEmpty()) {
            return spSet.getMbo(0);
        }
        return sparePartMbo;
    }
    
    public boolean isUncommitted() {
        return this.uncommitted;
    }
    
    public void setUncommitted(final boolean uncommitted) {
        this.uncommitted = uncommitted;
    }
    
    public void setSharedInvBalancesUpdatedFlag(final boolean updated) {
        this.sharedInvBalancesHasBeenUpdated = updated;
    }
    
    public void setInvReserveUpdatedFlag(final boolean updated) {
        this.invReserveUpdated = updated;
    }
    
    public void setCheckNegBalanceFlag(final boolean flag) throws MXException, RemoteException {
        this.checkNegBalance = flag;
    }
    
    boolean getCheckNegBalanceFlag() throws MXException, RemoteException {
        return this.checkNegBalance;
    }
    
    @Override
    public void createMatUseTransRecordforLifoFifo(final MboRemote inv) throws MXException, RemoteException {
        if (this.isReturn()) {
            return;
        }
        MboRemote invlifofifocost = null;
        double qty = this.getDouble("positivequantity");
        final String conditionCode = this.getString("conditionCode");
        final MboSetRemote invlifofifocostset = ((Inventory)inv).getInvLifoFifoCostRecordSetSorted(conditionCode);
        if (invlifofifocostset.isEmpty()) {
            throw new MXApplicationException("inventory", "missingQuantity");
        }
        int i = 0;
        MatUseTrans newMatMbo = this;
        newMatMbo.setValue("split", true, 2L);
        while ((invlifofifocost = invlifofifocostset.getMbo(i)) != null) {
            if (invlifofifocost.getDouble("quantity") == 0.0) {
                ++i;
            }
            else {
                if (invlifofifocost.getDouble("quantity") == qty) {
                    newMatMbo.setValue("positivequantity", invlifofifocost.getDouble("quantity"), 2L);
                    newMatMbo.setValue("unitcost", invlifofifocost.getDouble("unitcost"), 2L);
                    invlifofifocost.setValue("quantity", 0, 11L);
                    break;
                }
                if (invlifofifocost.getDouble("quantity") > qty) {
                    newMatMbo.setValue("positivequantity", qty, 2L);
                    newMatMbo.setValue("unitcost", invlifofifocost.getDouble("unitcost"), 2L);
                    qty = MXMath.subtract(invlifofifocost.getDouble("quantity"), qty);
                    invlifofifocost.setValue("quantity", qty, 11L);
                    break;
                }
                newMatMbo.setValue("positivequantity", invlifofifocost.getDouble("quantity"), 2L);
                newMatMbo.setValue("unitcost", invlifofifocost.getDouble("unitcost"), 2L);
                qty = MXMath.subtract(qty, invlifofifocost.getDouble("quantity"));
                invlifofifocost.setValue("quantity", 0, 11L);
                newMatMbo = (MatUseTrans)newMatMbo.copy();
                ++i;
            }
        }
    }
    
    public void setWorkOrderUpdatedFlag(final boolean updated) {
        this.workOrderUpdated = updated;
    }
    
    public InvReserveRemote getInvReserveInVector(final Vector v, final MboRemote thisInvReserve) throws MXException, RemoteException {
        final String requestnum = thisInvReserve.getString("requestnum");
        for (int i = 0; i < v.size(); ++i) {
            final Object obj = v.elementAt(i);
            final InvReserveRemote inv = (InvReserveRemote)obj;
            if (inv.getString("requestnum").equals(requestnum)) {
                return inv;
            }
        }
        return null;
    }
}