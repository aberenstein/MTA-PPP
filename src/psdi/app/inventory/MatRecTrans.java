package psdi.app.inventory;

import psdi.app.asset.Asset;
import psdi.app.location.LocationSetRemote;
import psdi.app.asset.AssetSetRemote;
import java.util.Hashtable;
import psdi.app.common.RoundToScale;
import psdi.app.invoice.InvoiceCostRemote;
import psdi.app.invoice.InvoiceLineRemote;
import java.util.Calendar;
import java.util.GregorianCalendar;
import psdi.app.contract.Contract;
import psdi.app.item.ItemOrgInfoSetRemote;
import psdi.app.inventory.virtual.AssetInputRemote;
import psdi.app.inventory.virtual.AssetInputSet;
import psdi.mbo.MboValueInfo;
import psdi.app.common.Taxes;
import psdi.app.common.Cost;
import psdi.security.UserInfo;
import psdi.app.invoice.InvoiceServiceRemote;
import java.util.HashMap;
import psdi.app.common.receipt.ReceiptMboSet;
import psdi.util.MXMath;
import psdi.app.site.SiteServiceRemote;
import psdi.server.MXServerRemote;
import psdi.util.MXApplicationYesNoCancelException;
import psdi.server.MXServer;
import psdi.mbo.MboValue;
import psdi.app.inventory.virtual.RotInspection;
import psdi.mbo.Mbo;
import psdi.app.asset.AssetRemote;
import psdi.app.workorder.WO;
import psdi.app.workorder.WOSetRemote;
import psdi.app.workorder.WORemote;
import psdi.app.currency.CurrencyServiceRemote;
import psdi.app.company.CompanyRemote;
import psdi.app.company.CompanySetRemote;
import psdi.app.item.InvVendorRemote;
import psdi.app.item.InvVendorSetRemote;
import psdi.app.item.ItemServiceRemote;
import psdi.app.invoice.InvoiceRemote;
import psdi.app.site.SiteRemote;
import psdi.app.location.LocationRemote;
import psdi.app.inventory.virtual.KitRemote;
import psdi.app.item.ItemStructRemote;
import psdi.mbo.Translate;
import java.util.Date;
import psdi.app.po.POLineRemote;
import psdi.app.po.PORemote;
import psdi.app.financial.FinancialServiceRemote;
import psdi.app.currency.CurrencyService;
import psdi.server.AppService;
import psdi.txn.MXTransaction;
import psdi.mbo.MboSetRemote;
import psdi.mbo.SqlFormat;
import psdi.app.po.ShipmentRemote;
import psdi.app.item.ItemRemote;
import psdi.app.common.TaxUtility;
import psdi.txn.Transactable;
import java.rmi.RemoteException;
import psdi.util.MXException;
import psdi.mbo.MboSet;
import psdi.app.item.ItemSetRemote;
import psdi.app.location.LocationServiceRemote;
import psdi.util.MXApplicationException;
import psdi.mbo.MboRemote;
import java.util.Vector;
import psdi.app.common.receipt.ReceiptMbo;

public class MatRecTrans extends ReceiptMbo implements MatRecTransRemote
{
    boolean isApprovingReceipt;
    boolean switchoffWOUpdate;
    private boolean transferFromNonInventory;
    String rotAssetNum;
    Vector<String> rotAssetsVector;
    private boolean toInvBalUpdated;
    private boolean fromInvBalUpdated;
    private boolean invReserveUpdated;
    private boolean checkNegBalance;
    protected boolean useInitialCurBal;
    protected boolean requestFulfilled;
    public boolean binNumSet;
    private MboRemote courierLaborMatRec;
    boolean recordUpdated;
    public boolean needToExecuteAppValidate;
    private boolean poLineUpdated;
    private boolean approveAfterCreatingAssets;
    private MXApplicationException conversionException;
    private LocationServiceRemote locService;
    private ItemSetRemote kitComponentsToAddToStore;
    
    public MatRecTrans(final MboSet ms) throws MXException, RemoteException {
        super(ms);
        cust.component.Logger.Log("MatRecTrans.MatRecTrans");
        this.isApprovingReceipt = false;
        this.switchoffWOUpdate = false;
        this.transferFromNonInventory = false;
        this.rotAssetNum = "";
        this.rotAssetsVector = new Vector<String>();
        this.toInvBalUpdated = false;
        this.fromInvBalUpdated = false;
        this.invReserveUpdated = false;
        this.checkNegBalance = true;
        this.useInitialCurBal = false;
        this.requestFulfilled = false;
        this.binNumSet = false;
        this.courierLaborMatRec = null;
        this.recordUpdated = false;
        this.needToExecuteAppValidate = true;
        this.poLineUpdated = false;
        this.approveAfterCreatingAssets = true;
        this.conversionException = null;
        this.locService = null;
        this.kitComponentsToAddToStore = null;
    }
    
    @Override
    public void init() throws MXException {
        cust.component.Logger.Log("MatRecTrans.init");
        super.init();
        try {
            final MboSetRemote mboSet = this.getThisMboSet();
            final MXTransaction trn = mboSet.getMXTransaction();
            if (this.toBeAdded() || this.getDouble("qtyheld") <= 0.0 || !this.isCourierOrLabor() || !this.isTransfer() || this.isNull("ponum")) {
                trn.setIndexOf(mboSet, 0);
            }
            final String[] alwaysReadOnly = { "statuschangeby", "statusdate", "issue", "issuetype", "prorated", "status", "exchangerate", "exchangerate2", "outside", "currencycode", "transdate", "curbal", "totalcurbal", "quantity", "oldavgcost", "qtyheld", "qtyrequested", "proratecost", "linecost", "linecost2", "loadedcost", "unitcost", "displayunitcost", "financialperiod", "wonum", "assetnum", "courier", "inspectedqtydsply" };
            this.setFieldFlag(alwaysReadOnly, 7L, true);
            this.setFieldFlag("invoicenum", 7L, this.getInvoiceMgtMaxVar());
            TaxUtility.getTaxUtility().setTaxesReadonly(this, "TAXCODE", true);
            TaxUtility.getTaxUtility().setTaxesReadonly(this, "TAX", true);
            if (!this.toBeAdded()) {
                this.setFieldFlag("conversion", 7L, true);
                this.setValue("sourcembo", "MATRECTRANS", 3L);
                if (!this.getString("itemnum").equals("") && this.getReceiptStatus().equalsIgnoreCase("WINSP") && ((ItemRemote)this.getMboSet("ITEM").getMbo(0)).isLotted()) {
                    this.setLottedEditibilityFlags(true);
                }
                else if (this.getPO() != null && this.getReceiptStatus().equalsIgnoreCase("WINSP") && this.getPOLine() != null && this.getPOLine().getBoolean("inspectionrequired")) {
                    this.setInspectionRequiredEditibilityFlags(true);
                }
                else if (this.getPO() != null && this.getDouble("qtyheld") > 0.0 && this.isCourierOrLabor() && this.isTransfer()) {
                    this.setEditibilityFlags(true);
                    final String[] approveRelatedFields = { "status", "statusedate" };
                    this.setFieldFlag(approveRelatedFields, 7L, true);
                    this.setFieldFlag("qtyheld", 7L, false);
                }
                else if (this.getOwner() != null && this.getOwner() instanceof ShipmentRemote && this.getReceiptStatus().equalsIgnoreCase("WINSP")) {
                    this.setInspectionRequiredEditibilityFlags(true);
                    this.setFieldFlag("receiptquantity", 7L, true);
                    this.setFieldFlag("shipmentlinenum", 7L, true);
                }
                else if (this.getOwner() != null && this.getOwner().isBasedOn("ROTINSPECTION")) {
                    this.setFieldFlag("accepted", 7L, false);
                    this.setFieldFlag("rejected", 7L, false);
                    this.setFieldFlag("rotassetnum", 7L, true);
                    this.setFieldFlag("frombin", 7L, true);
                }
                else {
                    this.setFlag(7L, true);
                }
                double unitcost = this.getDouble("unitcost");
                if (!this.isNull("conversion")) {
                    unitcost *= this.getDouble("conversion");
                }
                this.setValue("pounitcost", unitcost, 11L);
                this.setValue("polinecost", this.getDouble("receiptquantity") * this.getDouble("pounitcost"), 11L);
                if (!this.isNull("tolot")) {
                    final MboRemote poLine = super.getPOLine();
                    MboRemote invlotRemote = null;
                    if (this.isHolding() && poLine != null && !poLine.getString("storeloc").equals("")) {
                        final String item = this.getString("itemnum");
                        final String location = poLine.getString("storeloc");
                        final String tolot = this.getString("tolot");
                        final String site = this.getString("siteid");
                        final String set = this.getString("itemsetid");
                        final SqlFormat sqf = new SqlFormat(this, "lotnum = :1 and itemnum = :2 and location = :3 and itemsetid = :4 and siteid = :5");
                        sqf.setObject(1, "INVLOT", "lotnum", tolot);
                        sqf.setObject(2, "INVLOT", "itemnum", item);
                        sqf.setObject(3, "INVLOT", "location", location);
                        sqf.setObject(4, "INVLOT", "itemsetid", set);
                        sqf.setObject(5, "INVLOT", "siteid", site);
                        final MboSetRemote invLotSet = this.getMboSet("$getInitINVLOT" + tolot + "_" + item + "_" + location + "_" + set + "_" + site, "INVLOT", sqf.format());
                        invlotRemote = invLotSet.getMbo(0);
                        invLotSet.reset();
                    }
                    else {
                        invlotRemote = this.getMboSet("INVLOTTOLOT").getMbo(0);
                        this.getMboSet("INVLOTTOLOT").reset();
                    }
                    if (invlotRemote != null) {
                        this.setValue("mfglotnum", invlotRemote.getString("mfglotnum"), 3L);
                        this.setValue("shelflife", invlotRemote.getString("shelflife"), 3L);
                        this.setValue("useby", invlotRemote.getDate("useby"), 3L);
                        this.setFieldFlag("shelflife", 7L, true);
                        this.setFieldFlag("useby", 7L, true);
                        this.setFieldFlag("mfglotnum", 7L, true);
                    }
                }
                this.setShipmentMap();
            }
            else if (this.getPO() != null && this.getReceiptStatus().equalsIgnoreCase("WINSP") && this.getPOLine().getBoolean("inspectionrequired")) {
                this.setInspectionRequiredEditibilityFlags(true);
            }
            else {
                this.setFieldFlag("rejectqty", 7L, true);
            }
        }
        catch (RemoteException ex) {}
    }
    
    public boolean getBinNumFlag() {
        return this.binNumSet;
    }
    
    public void setBinNumFlag(final boolean binNumFlag) {
        this.binNumSet = binNumFlag;
    }
    
    @Override
    public void add() throws MXException, RemoteException {
        cust.component.Logger.Log("MatRecTrans.add");
        final Date systemDate = ((AppService)this.getMboServer()).getMXServer().getDate();
        final MatRecTransSet thisSet = (MatRecTransSet)this.getThisMboSet();
        final boolean toExecuteCompletAdd = thisSet.toExecuteCompleteAdd();
        if (toExecuteCompletAdd) {
            final CurrencyService curService = (CurrencyService)((AppService)this.getMboServer()).getMXServer().lookup("CURRENCY");
            this.setValue("CURRENCYCODE", curService.getBaseCurrency1(this.getString("orgid"), this.getUserInfo()), 2L);
        }
        final MboRemote owner = this.getOwner();
        if (owner != null) {
            this.setValue("fromsiteid", owner.getString("siteid"), 11L);
            this.setValue("newsite", owner.getString("siteid"), 11L);
            final String name = owner.getName();
            if (!name.equals("PO") && !name.equals("POLINE") && !name.equals("LOCATIONS") && !name.equals("ITEM") && !name.equals("INVENTORY") && !name.equals("TOOLINV") && !name.equals("INVOICE") && !name.equals("MATRECTRANS") && !name.equals("INVUSELINE") && !name.equals("INVUSE") && !name.equals("SHIPMENT") && !name.equals("ROTINSPECTION")) {
                throw new MXApplicationException("inventory", "matRecTransNoAdd");
            }
            this.setValue("PRORATED", false, 11L);
            if (name.equals("INVENTORY")) {
                final double defissuecost = ((Inventory)owner).getDefaultIssueCost();
                this.setValue("UNITCOST", defissuecost, 11L);
                this.setValue("DISPLAYUNITCOST", this.getString("unitcost"), 11L);
            }
            else {
                this.setValue("UNITCOST", 0.0, 11L);
                this.setValue("DISPLAYUNITCOST", 0.0, 11L);
            }
            if (name.equals("LOCATIONS")) {
                String value = "";
                if (thisSet == owner.getMboSet("MATRECTRANSOUT")) {
                    this.setFieldFlag("assetnum", 7L, false);
                    this.setValue("fromsiteid", owner.getString("siteid"), 2L);
                    this.setValue("fromstoreloc", owner.getString("location"), 2L);
                    value = this.getTranslator().toExternalDefaultValue("ISSUETYP", "TRANSFER", this);
                    this.setValue("ISSUETYPE", value, 2L);
                }
                else if (thisSet == owner.getMboSet("MATRECTRANSIN")) {
                    this.setValue("tostoreloc", owner.getString("location"), 2L);
                    value = this.getTranslator().toExternalDefaultValue("ISSUETYP", "TRANSFER", this);
                    this.setValue("ISSUETYPE", value, 2L);
                    this.setFieldFlag("assetnum", 7L, false);
                    this.setValue("siteid", owner.getString("siteid"), 2L);
                }
                else if (thisSet == owner.getMboSet("MATRECTRANSMOVEIN")) {
                    this.transferFromNonInventory = true;
                    if (toExecuteCompletAdd) {
                        value = this.getTranslator().toExternalDefaultValue("ISSUETYP", "TRANSFER", this);
                        this.setValue("ISSUETYPE", value, 2L);
                    }
                    else {
                        value = this.getTranslator().toExternalDefaultValue("ISSUETYP", "TRANSFER", this);
                        this.setValue("ISSUETYPE", value, 3L);
                    }
                }
                if (toExecuteCompletAdd) {
                    this.setValue("itemsetid", this.getMboSet("$orgs", "ORGANIZATION", "orgid='" + this.getString("orgid") + "'").getMbo(0).getString("itemsetid"), 11L);
                    this.setFieldFlag("linecost", 7L, true);
                    this.setFieldFlag("currencylinecost", 7L, true);
                }
            }
        }
        else {
            this.setValue("fromsiteid", this.getString("siteid"), 11L);
            this.setValue("newsite", this.getString("siteid"), 11L);
        }
        this.setValue("sourcembo", "MATRECTRANS", 3L);
        this.setValue("receiptquantity", 1, 11L);
        this.setValue("quantity", 1, 11L);
        this.setValue("ACTUALDATE", systemDate, 11L);
        this.setValue("TRANSDATE", systemDate, 11L);
        this.setValue("ENTERBY", this.getUserInfo().getUserName(), 11L);
        final FinancialServiceRemote finService = (FinancialServiceRemote)((AppService)this.getMboServer()).getMXServer().lookup("FINANCIAL");
        try {
            if (toExecuteCompletAdd) {
                final String financialperiod = finService.getActiveFinancialPeriod(this.getUserInfo(), this.getDate("transdate"), this.getString("orgid"));
                this.setValue("financialperiod", financialperiod, 11L);
            }
        }
        catch (MXException ex) {}
        if (toExecuteCompletAdd) {
            this.setValue("linetype", this.getTranslator().toExternalDefaultValue("LINETYPE", "ITEM", this), 11L);
        }
        if (this.isNull("issuetype")) {
            if (owner instanceof ShipmentRemote) {
                this.setValue("issuetype", this.getTranslator().toExternalDefaultValue("ISSUETYP", "SHIPRECEIPT", this), 11L);
            }
            else {
                this.setValue("issuetype", this.getTranslator().toExternalDefaultValue("ISSUETYP", "RECEIPT", this), 11L);
            }
        }
        this.setValue("issue", false, 11L);
        this.setValue("exchangerate", 1, 11L);
        this.setValue("exchangerate2", 1, 11L);
        this.setValue("curbal", 0, 11L);
        this.setValue("inspectedqty", 0, 11L);
        this.setValue("rejectqty", 0, 11L);
        this.setValue("acceptedqty", 0, 11L);
        this.setValue("totalcurbal", 0, 11L);
        this.setValue("outside", false, 11L);
        this.setValue("actualcost", 1, 11L);
        this.setValue("displayunitcost", 0.0, 11L);
        this.setValue("linecost", this.getDouble("receiptquantity") * this.getDouble("unitcost"), 11L);
        this.setValue("loadedcost", 0, 11L);
        this.setValue("enteredastask", false, 11L);
        if (owner != null) {
            if (owner instanceof PORemote) {
                this.setValue("porevisionnum", owner.getString("revisionnum"), 2L);
                this.setValue("ponum", owner.getString("ponum"), 2L);
            }
            if (owner instanceof POLineRemote) {
                this.setValue("porevisionnum", owner.getString("revisionnum"), 2L);
                this.setValue("ponum", owner.getString("ponum"), 2L);
            }
        }
    }
    
    @Override
    public void canDelete() throws MXException, RemoteException {
        if (!this.toBeAdded()) {
            throw new MXApplicationException("inventory", "matrectrans_cannotdelete");
        }
    }
    
    @Override
    public boolean isTransfer() throws MXException, RemoteException {
        final Translate tr = this.getTranslator();
        return !this.isNull("ISSUETYPE") && tr.toInternalString("ISSUETYP", this.getString("issuetype")).equalsIgnoreCase("TRANSFER");
    }
    
    public boolean isShipReceipt() throws MXException, RemoteException {
        final Translate tr = this.getTranslator();
        return !this.isNull("ISSUETYPE") && tr.toInternalString("ISSUETYP", this.getString("issuetype")).equalsIgnoreCase("SHIPRECEIPT");
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
    
    protected void doKitBreakUpdates(final ItemStructRemote kitComponent, final KitRemote kit) throws MXException, RemoteException {
        final double componentQuantityToBeFreedFromKit = kitComponent.getDouble("kitquantity");
        final Inventory toInventoryMbo = (Inventory)this.getSharedInventoryForKit();
        this.doKitBreakInvBalanceUpdates(toInventoryMbo, componentQuantityToBeFreedFromKit, kit);
        this.kitComponentInventoryUpdate(toInventoryMbo);
    }
    
    private void doKitBreakInvBalanceUpdates(final InventoryRemote toInventoryMbo, final double componentQuantityToBeFreedFromKit, final KitRemote kit) throws MXException, RemoteException {
        final String toInvDefaultBinnum = toInventoryMbo.getString("binnum");
        final MboSetRemote invBalancesToBuildKit = toInventoryMbo.getInvBalancesSetForKitComponent(toInvDefaultBinnum);
        MboRemote toInvBal = invBalancesToBuildKit.getMbo(0);
        if (toInvBal == null) {
            toInvBal = ((Inventory)toInventoryMbo).addInvBalanceAndRelatedObjects(componentQuantityToBeFreedFromKit);
        }
        double oldBalance = 0.0;
        if (this.useInitialCurBal) {
            oldBalance = toInvBal.getMboInitialValue("curbal").asDouble();
        }
        else {
            oldBalance = toInvBal.getDouble("curbal");
        }
        this.setValue("curbal", oldBalance, 2L);
        this.setValue("totalcurbal", toInventoryMbo.getCurrentBalance(null, null), 2L);
        double curBalToUpdate = componentQuantityToBeFreedFromKit;
        if (!toInvBal.toBeAdded()) {
            curBalToUpdate = componentQuantityToBeFreedFromKit + oldBalance;
        }
        ((InvBalances)toInvBal).updateCurrentBalance(curBalToUpdate);
    }
    
    private void kitComponentInventoryUpdate(final Inventory kitComponent) throws MXException, RemoteException {
        final InvCost invcost = (InvCost)kitComponent.getInvCostRecord();
        this.updateInventory(kitComponent, null, invcost);
        invcost.increaseAccumulativeReceiptQty(this.getDouble("quantity"));
    }
    
    public boolean isTransferNoPO() throws MXException, RemoteException {
        return this.isTransfer() && this.isNull("ponum");
    }
    
    private boolean isTransferWithPO() throws MXException, RemoteException {
        return this.isTransfer() && !this.isNull("ponum");
    }
    
    public boolean isReceipt() throws MXException, RemoteException {
        if (this.isNull("issuetype")) {
            return false;
        }
        final Translate tr = this.getTranslator();
        return tr.toInternalString("ISSUETYP", this.getString("issuetype")).equalsIgnoreCase("RECEIPT");
    }
    
    public boolean isReturn() throws MXException, RemoteException {
        final Translate tr = this.getTranslator();
        return tr.toInternalString("ISSUETYP", this.getString("issuetype")).equalsIgnoreCase("RETURN");
    }
    
    public boolean isInvoice() throws MXException, RemoteException {
        final Translate tr = this.getTranslator();
        return tr.toInternalString("ISSUETYP", this.getString("issuetype")).equalsIgnoreCase("INVOICE");
    }
    
    public boolean isMisclReceipt() throws MXException, RemoteException {
        final Translate tr = this.getTranslator();
        return tr.toInternalString("ISSUETYP", this.getString("issuetype")).equalsIgnoreCase("MISCLRECEIPT");
    }
    
    @Override
    public boolean isVoidReceipt() throws MXException, RemoteException {
        if (this.isNull("issuetype")) {
            return false;
        }
        final Translate tr = this.getTranslator();
        return tr.toInternalString("ISSUETYP", this.getString("issuetype")).equalsIgnoreCase("VOIDRECEIPT");
    }
    
    boolean isCourierOrLabor() throws MXException, RemoteException {
        final LocationRemote loc = (LocationRemote)this.getMboSet("LOCATIONS").getMbo(0);
        return loc != null && (loc.isCourier() || loc.isLabor());
    }
    
    @Override
    public boolean hasVoidReceipt() throws MXException, RemoteException {
        final MboSetRemote voidReceiptSet = this.getMboSet("RETURNVOIDRECEIPTS");
        return !voidReceiptSet.isEmpty();
    }
    
    @Override
    public boolean isHolding() throws MXException, RemoteException {
        final LocationRemote loc = (LocationRemote)this.getMboSet("LOCATIONS").getMbo(0);
        return loc != null && loc.isHolding();
    }
    
    boolean willBeHolding() throws MXException, RemoteException {
        final LocationRemote loc = (LocationRemote)this.getMboSet("FROMLOCATION").getMbo(0);
        return loc != null && loc.isHolding();
    }
    
    boolean isFromCourierOrLabor() throws MXException, RemoteException {
        final LocationRemote loc = (LocationRemote)this.getMboSet("FROMLOCATION").getMbo(0);
        return loc != null && (loc.isCourier() || loc.isLabor());
    }
    
    boolean isStore() throws MXException, RemoteException {
        final LocationRemote loc = (LocationRemote)this.getMboSet("LOCATIONS").getMbo(0);
        return loc != null && loc.isStore();
    }
    
    boolean isFromStore() throws MXException, RemoteException {
        final LocationRemote loc = (LocationRemote)this.getMboSet("FROMLOCATION").getMbo(0);
        return loc != null && loc.isStore();
    }
    
    MboRemote getHoldingLocationForSite(final String siteid) throws MXException, RemoteException {
        final LocationRemote loc = (LocationRemote)this.getMboSet("ALLLOCSFORSITE").getMbo(0);
        if (loc == null) {
            return null;
        }
        return loc.getHoldingLocationForSite(siteid);
    }
    
    boolean isApprovingReceipt() throws MXException, RemoteException {
        return this.isApprovingReceipt;
    }
    
    @Override
    public boolean isReject() throws MXException, RemoteException {
        return this.getTranslator().toInternalString("ISSUETYP", this.getString("issuetype")).equalsIgnoreCase("RETURN") && this.willBeHolding();
    }
    
    public boolean isShipReject() throws MXException, RemoteException {
        return this.getTranslator().toInternalString("ISSUETYP", this.getString("issuetype")).equalsIgnoreCase("SHIPRETURN") && this.willBeHolding();
    }
    
    public String getReceiptStatus() throws MXException, RemoteException {
        if (!this.isNull("status")) {
            return this.getTranslator().toInternalString("RECEIPTSTATUS", this.getString("status"));
        }
        return "";
    }
    
    @Override
    public MboRemote getClearingAcct() throws MXException, RemoteException {
        final SiteRemote tositeMbo = (SiteRemote)this.getOwner().getMboSet("SITE").getMbo(0);
        final MboSetRemote orgSet = tositeMbo.getMboSet("ORGANIZATION");
        final MboRemote orgmbo = orgSet.getMbo(0);
        return orgmbo;
    }
    
    boolean isInspectionRequired() throws MXException, RemoteException {
        final MboRemote owner = this.getOwner();
        if (owner != null && owner instanceof PORemote) {
            final POLineRemote poLineMbo = this.getPOLine();
            if (poLineMbo != null) {
                return poLineMbo.isInspectionRequired();
            }
        }
        else if (owner != null && owner.isBasedOn("SHIPMENT")) {
            final MboRemote shipmentLineRemote = this.getMboSet("SHIPMENTLINE").getMbo(0);
            if (shipmentLineRemote != null) {
                final MboRemote invUseLineRemote = shipmentLineRemote.getMboSet("INVUSELINE").getMbo(0);
                if (invUseLineRemote != null) {
                    return invUseLineRemote.getBoolean("inspectionrequired");
                }
            }
        }
        else {
            final MboRemote itemRemote = this.getMboSet("ITEM").getMbo(0);
            if (itemRemote != null) {
                return itemRemote.getBoolean("inspectionrequired");
            }
        }
        return false;
    }
    
    void validateBinLot(final Inventory inv, final String binnum, final String lotnum) throws MXException, RemoteException {
        final InvBalances invBal = inv.getInvBalanceRecord(binnum, lotnum);
        if (invBal == null) {
            throw new MXApplicationException("inventory", "noBalanceRecord");
        }
    }
    
    MboRemote validateInventory(final String location) throws MXException, RemoteException {
        final SqlFormat sqf = new SqlFormat(this, "itemnum = :itemnum and location = :1");
        sqf.setObject(1, "INVENTORY", "location", location);
        final MboSetRemote invSet = this.getMboSet("$getInvSet**" + location, "INVENTORY", sqf.format());
        if (invSet.isEmpty()) {
            invSet.reset();
            throw new MXApplicationException("inventory", "invbalNotInInventory");
        }
        return invSet.getMbo(0);
    }
    
    @Override
    public void approve(Date statusDate) throws MXException, RemoteException {
        cust.component.Logger.Log("MatRecTrans.approve");
        final MboRemote ownerRemote = this.getOwner();
        this.canApprove();
        if (ownerRemote != null && ownerRemote.isModified() && !(this.getOwner() instanceof InvoiceRemote)) {
            throw new MXApplicationException("inventory", "mustSaveBeforeApprove");
        }
        if (!this.isNull("Belongsto")) {
            throw new MXApplicationException("inventory", "approveSubReceipt");
        }
        final MboRemote itemRemote = this.getMboSet("ITEM").getMbo(0);
        if (itemRemote != null && ((ItemRemote)itemRemote).isLotted()) {
            if (this.getString("tolot").equals("")) {
                throw new MXApplicationException("inventory", "enterToLot");
            }
            this.setFieldFlag("tolot", 7L, true);
            this.setFieldFlag("useby", 7L, true);
            this.setFieldFlag("mfglotnum", 7L, true);
            this.setFieldFlag("shelflife", 7L, true);
        }
        if (statusDate == null) {
            statusDate = ((AppService)this.getMboServer()).getMXServer().getDate();
        }
        final POLineRemote poLineMbo = this.getPOLine();
        if (this.isInspectionRequired()) {
            if (this.getReceiptStatus().equals("WASSET") && this.getDouble("inspectedqty") - this.getDouble("rejectqty") > 0.0) {
                final MatRecTransRemote matrec = (MatRecTransRemote)this.getThisMboSet().addAtEnd();
                this.copyMatRecTransToMatRecTrans(matrec, "TRANSFER");
            }
            if (this.getReceiptStatus().equals("WINSP")) {
                if (((!this.isNull("itemnum") && !this.getBoolean("ITEM.rotating")) || this.getTranslator().toInternalString("LINETYPE", this.getString("linetype")).equalsIgnoreCase("MATERIAL")) && this.getDouble("acceptedqty") > 0.0) {
                    final MatRecTransRemote matrec = (MatRecTransRemote)this.getThisMboSet().addAtEnd();
                    this.copyMatRecTransToMatRecTrans(matrec, "TRANSFER");
                }
                if (this.getDouble("rejectqtydisplay") > 0.0) {
                    this.setValue("rejectqty", this.getDouble("rejectqtydisplay") + this.getDouble("rejectqty"), 2L);
                    final MatRecTransRemote matrec = (MatRecTransRemote)this.getThisMboSet().addAtEnd();
                    this.isApprovingReceipt = true;
                    if (this.getOwner() instanceof ShipmentRemote) {
                        this.copyMatRecTransToMatRecTrans(matrec, "SHIPRETURN");
                    }
                    else {
                        this.copyMatRecTransToMatRecTrans(matrec, "RETURN");
                    }
                }
            }
        }
        else if (this.getReceiptStatus().equals("WASSET")) {
            if (ownerRemote != null && ownerRemote instanceof ShipmentRemote) {
                final MatRecTransRemote matrec = (MatRecTransRemote)this.getThisMboSet().addAtEnd();
                this.copyMatRecTransToMatRecTrans(matrec, "TRANSFER");
            }
            else if (this.getDouble("acceptedqty") > 0.0) {
                final MatRecTransRemote matrec = (MatRecTransRemote)this.getThisMboSet().addAtEnd();
                this.copyMatRecTransToMatRecTrans(matrec, "TRANSFER");
            }
        }
        this.setValue("statuschangeby", this.getUserInfo().getUserName(), 11L);
        this.setValue("statusdate", statusDate, 11L);
        if (this.isReceipt() || this.isShipReceipt()) {
            if (!this.isNull("itemnum") && this.getBoolean("ITEM.rotating") && this.getReceiptStatus().equals("WINSP")) {
                this.setValue("inspectedqty", this.getDouble("inspectedqty") + this.getDouble("acceptedqty") + this.getDouble("rejectqtydisplay"), 11L);
                if (this.getDouble("inspectedqty") == this.getDouble("receiptquantity") && this.getDouble("acceptedqty") == 0.0) {
                    this.setValue("status", "!COMP!", 2L);
                }
                else if (this.getDouble("inspectedqty") == this.getDouble("receiptquantity")) {
                    this.setValue("status", "!WASSET!", 2L);
                }
            }
            else if (this.isInspectionRequired()) {
                if ((!this.isNull("itemnum") && !this.getBoolean("ITEM.rotating")) || this.getTranslator().toInternalString("LINETYPE", this.getString("linetype")).equalsIgnoreCase("MATERIAL")) {
                    this.setValue("inspectedqty", this.getDouble("inspectedqty") + this.getDouble("acceptedqty") + this.getDouble("rejectqtydisplay"), 11L);
                }
                this.setValue("receiptquantity", this.getDouble("acceptedqty"), 11L);
                if (!this.isNull("conversion") && this.getDouble("conversion") != 0.0) {
                    if (this.getDouble("INSPECTEDQTYDSPLY") <= this.getDouble("acceptedqty") + this.getDouble("REJECTQTYDISPLAY")) {
                        this.setValue("status", "!COMP!", 2L);
                        if (poLineMbo != null) {
                            this.updatePoLineRejected();
                        }
                    }
                }
                else if (this.getDouble("INSPECTEDQTYDSPLY") <= this.getDouble("acceptedqty") + this.getDouble("REJECTQTYDISPLAY")) {
                    this.setValue("status", "!COMP!", 2L);
                    this.updatePoLineRejected();
                }
            }
            else {
                this.setValue("receiptquantity", this.getDouble("quantity"), 11L);
                this.setValue("status", "!COMP!", 2L);
                this.updatePoLineRejected();
            }
        }
        final MboSetRemote children = this.getMboSet("MATRECTRANS");
        MboRemote child = null;
        for (int i = 0; (child = children.getMbo(i)) != null; ++i) {
            child.setValue("statuschangeby", this.getUserInfo().getUserName(), 11L);
            child.setValue("statusdate", statusDate, 11L);
            child.setValue("status", "!COMP!", 11L);
        }
        this.isApprovingReceipt = true;
        if (ownerRemote != null && !(ownerRemote instanceof ShipmentRemote)) {
            final PORemote poMbo = this.getPO();
            this.updateRelatedObjects(poMbo, poLineMbo);
        }
    }
    
    private void updatePoLineRejected() throws MXException, RemoteException {
        double receiptConversion = 0.0;
        if (!this.isNull("conversion")) {
            receiptConversion = this.getDouble("conversion");
        }
        if (receiptConversion == 0.0) {
            receiptConversion = 1.0;
        }
        if (this.isModified() && this.getDouble("rejectqty") > 0.0) {
            this.getPOLine().updatePOPOLine(0.0, this.getDouble("REJECTQTY"), 0.0, this.getPO(), receiptConversion);
        }
    }
    
    MboRemote getSharedInventory(final String storeLoc) throws MXException, RemoteException {
        cust.component.Logger.Log("MatRecTrans.getSharedInventory");
        final MboRemote owner = this.getOwner();
        if (owner instanceof InventoryRemote && owner.getString("location").equals(storeLoc)) {
            return owner;
        }
        final SqlFormat sqf = new SqlFormat(this, "itemnum = :itemnum and location = :1");
        sqf.setObject(1, "INVENTORY", "location", storeLoc);
        final MboSetRemote invSet = ((MboSet)this.getThisMboSet()).getSharedMboSet("INVENTORY", sqf.format());
        if (invSet != null) {
            invSet.setOwner(this);
        }
        if (invSet == null || invSet.isEmpty()) {
            return null;
        }
        return invSet.getMbo(0);
    }
    
    private MboRemote getSharedInventoryForKit() throws MXException, RemoteException {
        final MboRemote owner = this.getOwner();
        if (owner instanceof InventoryRemote && owner.getString("location").equals(this.getString("tostoreloc")) && owner.getString("itemnum").equals(this.getString("itemnum"))) {
            return owner;
        }
        final SqlFormat sqf = new SqlFormat(this, "itemnum = :itemnum and location = :tostoreloc and siteid = :siteid and itemsetid = :itemsetid");
        final MboSetRemote invSet = ((MboSet)this.getThisMboSet()).getSharedMboSet("INVENTORY", sqf.format());
        if (invSet != null) {
            invSet.setOwner(this);
        }
        if (invSet == null || invSet.isEmpty()) {
            return null;
        }
        return invSet.getMbo(0);
    }
    
    MboRemote getInvReserve(final String where) throws MXException, RemoteException {
        final SqlFormat sqf = new SqlFormat(this, where);
        final MboSetRemote invReserveSet = ((MboSet)this.getThisMboSet()).getSharedMboSet("INVRESERVE", sqf.format());
        if (invReserveSet == null || invReserveSet.isEmpty()) {
            return null;
        }
        final MboRemote invReserve = invReserveSet.getMbo(0);
        if (this.isNull("requestnum") && invReserve != null) {
            this.setValue("requestnum", invReserve.getString("requestnum"), 2L);
        }
        return invReserve;
    }
    
    private void createStdRecAdj(final double standardCost) throws MXException, RemoteException {
        cust.component.Logger.Log("MatRecTrans.createStdRecAdj");
        final InvTransSetRemote invTransSet = (InvTransSetRemote)this.getMboSet("$CreateInvTrans" + this.getString("itemnum"), "INVTRANS", "");
        final InvTrans invTrans = (InvTrans)invTransSet.add(2L);
        invTrans.setValue("matrectransid", this.getString("matrectransid"));
        invTrans.setValue("quantity", this.getDouble("quantity"));
        invTrans.setValue("oldcost", standardCost);
        invTrans.setValue("curbal", this.getDouble("curbal"));
        invTrans.setValue("newcost", this.getDouble("unitcost"));
        final double lineCost = this.getDouble("quantity") * standardCost - this.getDouble("quantity") * this.getDouble("unitcost");
        invTrans.setValue("linecost", lineCost);
        if (!this.isNull("linecost2")) {
        	cust.component.Logger.Log("linecost2#1");
            invTrans.setValue("linecost2", this.getDouble("linecost2"), 9L);
        }
        if (!this.isNull("exchangerate2")) {
            invTrans.setValue("exchangerate2", this.getDouble("exchangerate2"), 9L);
        }
    }
    
    public void updateInventory(final InventoryRemote invmbo, final PORemote poMbo, final InvCost invcost) throws MXException, RemoteException {
        cust.component.Logger.Log("MatRecTrans.updateInventory");
        if (invmbo == null) {
            return;
        }
        double oldAvgCost = 0.0;
        oldAvgCost = invmbo.getDouble("avgcost");
        if (oldAvgCost != 0.0) {
            this.setValue("oldavgcost", oldAvgCost, 11L);
        }
        else if (invcost != null) {
            this.setValue("oldavgcost", invcost.getDouble("avgcost"), 11L);
        }
        String pcid = "RCINV";
        if (this.isMisclReceipt()) {
            pcid = "INV";
        }
        if (this.useIntegration(invmbo, pcid)) {
            return;
        }
        final double quantity = this.getDouble("quantity");
        if (this.getTranslator().toInternalString("ISSUETYP", this.getString("issuetype")).equalsIgnoreCase("INVOICE") && this.getDouble("proratecost") != 0.0) {
            ((Inventory)invmbo).updateInventoryAverageCost(quantity, this.getDouble("loadedcost"), 1.0, invcost);
        }
        else if (this.getTranslator().toInternalString("ISSUETYP", this.getString("issuetype")).equalsIgnoreCase("KITBREAK")) {
            ((Inventory)invmbo).updateInventoryAverageCost(quantity, this.getDouble("linecost"), 1.0, invcost);
        }
        else {
            if (this.isShipReceipt()) {
                ((Inventory)invmbo).updateInventoryAverageCost(quantity, this.getDouble("loadedcost"), this.getDouble("exchangerate"), invcost);
            }
            ///AMB===v===
            /// Errores #1 y #2: anulación y devolución de recepción en ARS y en USD
            /// Antes de invocar updateInventoryAverageCost que actualiza el PPP del item se obtiene la fecha relevante del
            /// tipo de cambio, que es la fecha de la recepción, para que el cálculo se realice al tipo de cambio correcto.
            /*
            else {            	
                ((Inventory)invmbo).updateInventoryAverageCost(quantity, this.getDouble("loadedcost"), 1.0, invcost);
            }
            */
            else {
                final boolean sameStoreroom = this.getString("fromstoreloc").equalsIgnoreCase(this.getString("tostoreloc"));
                if (!sameStoreroom) {
            		if (this.getTranslator().toInternalString("ISSUETYP", this.getString("issuetype")).equalsIgnoreCase("VOIDRECEIPT") ||
            			this.getTranslator().toInternalString("ISSUETYP", this.getString("issuetype")).equalsIgnoreCase("RETURN")) 
            		{
            			// ANULACION DE RECEPCION: MARCO LA FECHA DEL RECIBO EN LUGAR DE LA FECHA ACTUAL
            			// PARA CALCULAR EL TIPO DE CAMBIO PARA EL PPP UD
            			((Inventory)invmbo).exchageDate = getActualDate();
             		}
                    cust.component.Logger.Log("---MARKER---");

                    ((Inventory)invmbo).updateInventoryAverageCost(quantity, this.getDouble("loadedcost"), 1.0, invcost);
                }
            } 
            ///AMB===^===
            
            if (this.getDouble("loadedcost") != 0.0 && this.isTransfer() && !this.isNull("rotassetnum") && !this.isNull("ponum") && !this.isNull("receiptref")) {
                this.setValue("loadedcost", 0.0, 11L);
                TaxUtility.getTaxUtility().setTaxattrValue(this, "TAX", 0.0, 11L);
            }
        }
        if (quantity > 0.0) {
            final boolean sameStoreroomSite = this.getString("fromstoreloc").equalsIgnoreCase(this.getString("tostoreloc")) && this.getString("fromsiteid").equalsIgnoreCase(this.getString("siteid"));
            if (!this.useIntegration(invmbo, "RCILC") && !sameStoreroomSite) {
                if (this.isShipReceipt()) {
                    invmbo.updateInventoryLastCost(this.getDouble("unitcost"), this.getDouble("exchangerate"), invcost);
                }
                else {
                    invmbo.updateInventoryLastCost(this.getDouble("unitcost"), 1.0, invcost);
                }
            }
        }
        this.adjustLeadTime(invmbo, poMbo);
    }
    
    ///AMB===v===
    /**
     * Obtiene la fecha efectiva para calcular el tipo de cambio de la devolución de inventario.
     * La fecha efectiva es la fecha de la recepción que se desea anular.
     *  
     * @return	la fecha de la recepción asociada a éste movimiento
     * @throws RemoteException
     * @throws MXException
     */
    private Date getActualDate() throws RemoteException, MXException
    {
    	MboSetRemote matRecTransSet = this.getMboSet("$MATRECTRANS2MATRECTRANS", "MATRECTRANS", "MATRECTRANSID = :RECEIPTREF");
        final MboRemote matRecTransRemote = matRecTransSet.getMbo(0);
        if (matRecTransRemote == null)
        {
            final String[] params = { "Error: No se encontró la transacción original." };
        	throw new MXApplicationException("messagebox", "CustomMessage", params);
        }
       	return matRecTransRemote.getDate("transdate");
    }
    ///AMB===^===
        
    private void adjustLeadTime(final InventoryRemote invmbo, final PORemote poMbo) throws MXException, RemoteException {
        if (poMbo == null) {
            return;
        }
        final LocLeadTimeSetRemote leadTimeSet = (LocLeadTimeSet)this.getMboSet("LOCLEADTIME");
        if (leadTimeSet.isEmpty()) {
            return;
        }
        final LocLeadTimeRemote leadTimeMbo = (LocLeadTimeRemote)leadTimeSet.getMbo(0);
        final double percentage = 1.0;
        Date orderDate;
        if (!poMbo.isNull("orderdate")) {
            orderDate = poMbo.getDate("orderdate");
        }
        else {
            orderDate = poMbo.getDate("statusdate");
        }
        final Date actualDate = this.getDate("actualdate");
        if (actualDate.before(orderDate)) {
            return;
        }
        final long resultTime = Math.abs(actualDate.getTime() - orderDate.getTime());
        final double diffInDays = resultTime / 8.64E7;
        final double newPercent = leadTimeMbo.getDouble("newpercent") / 100.0;
        final int deliveryTime = (int)((1.0 - newPercent) * invmbo.getDouble("deliverytime") + newPercent * diffInDays);
        if (deliveryTime >= 0) {
            invmbo.updateInventoryDeliveryTime(deliveryTime);
        }
    }
    
    private void updateInvVendor(final PORemote poMbo) throws MXException, RemoteException {
        if (poMbo == null) {
            return;
        }
        final ItemServiceRemote itemServ = (ItemServiceRemote)((AppService)this.getMboServer()).getMXServer().lookup("ITEM");
        final String vendor = poMbo.getString("vendor");
        final String where = itemServ.getInvVendorSql(vendor, this);
        final InvVendorSetRemote invSet = (InvVendorSetRemote)((MboSet)this.getThisMboSet()).getSharedMboSet("INVVENDOR", where);
        if (invSet.isEmpty()) {
            return;
        }
        final InvVendorRemote invVendMbo = (InvVendorRemote)invSet.getMbo(0);
        if (this.useIntegration(invVendMbo, "RCVLC")) {
            return;
        }
        final double exchangerate = this.getVendorCurrencyExchangeRate(vendor);
        double lastCost = this.getDouble("currencyunitcost") / exchangerate;
        if (!this.isNull("conversion") && this.getDouble("conversion") != 0.0) {
            lastCost *= this.getDouble("conversion");
        }
        invVendMbo.updateLastCost(lastCost, null);
        invVendMbo.setValue("orderunit", this.getString("RECEIVEDUNIT"), 2L);
        if (!this.isNull("conversion")) {
            invVendMbo.setValue("conversion", this.getDouble("conversion"), 2L);
        }
    }
    
    private double getVendorCurrencyExchangeRate(final String vendor) throws MXException, RemoteException {
        cust.component.Logger.Log("MatRecTrans.getVendorCurrencyExchangeRate");
    	final SqlFormat sqf = new SqlFormat(this, " company = :1");
        sqf.setObject(1, "COMPANIES", "company", vendor);
        final CompanySetRemote companySet = (CompanySetRemote)this.getMboSet("$getCompany" + vendor, "COMPANIES", sqf.format());
        if (companySet.isEmpty()) {
            return 1.0;
        }
        final CompanyRemote companyMbo = (CompanyRemote)companySet.getMbo(0);
        final String vendorCurrencyCode = companyMbo.getString("currencyCode");
        if (vendorCurrencyCode.equals(this.getString("currencycode"))) {
            return 1.0;
        }
        final CurrencyServiceRemote currService = (CurrencyServiceRemote)((AppService)this.getMboServer()).getMXServer().lookup("CURRENCY");
        double exchangerate = 1.0;
        final String baseCurrencyCode = currService.getBaseCurrency1(this.getString("orgid"), this.getUserInfo());
        try {
            exchangerate = currService.getCurrencyExchangeRate(this.getUserInfo(), vendorCurrencyCode, baseCurrencyCode, this.getDate("transdate"), this.getString("orgid"));
        }
        catch (MXApplicationException b) {
            exchangerate = 1.0;
        }
        return exchangerate;
    }
    
    private void updateWorkOrder(final PORemote poMbo, final POLineRemote poLineMbo) throws MXException, RemoteException {
        if (this.isSwitchoffWOUpdate()) {
            return;
        }
        WORemote woMbo = null;
        WORemote childMbo = null;
        woMbo = (WORemote)this.getWOReference();
        final InvReserve invReserveMbo = (InvReserve)this.getInvReserve("itemnum=:itemnum and itemsetid = :itemsetid and wonum=:wonum");
        if (invReserveMbo != null && !this.getBoolean("issue")) {
            invReserveMbo.incrActualQty(this.getDouble("quantity"));
        }
        if (woMbo == null) {
            String wonum = null;
            if (this.isNull("refwo")) {
                wonum = this.getString("wonum");
            }
            else {
                wonum = this.getString("refwo");
            }
            final SqlFormat sqf = new SqlFormat(this, "wonum=:1 and siteid=:siteid");
            sqf.setObject(1, "WORKORDER", "wonum", wonum);
            woMbo = (WORemote)((MboSet)this.getThisMboSet()).getSharedMboSet("WORKORDER", sqf.format()).getMbo(0);
            if (!woMbo.isModified()) {
                final MboRemote owner = this.getOwner();
                if (owner instanceof PORemote) {
                    final WORemote otherWO = (WORemote)((PORemote)owner).getSharedWorkorder(this, wonum);
                    if (otherWO != null && woMbo.getString("wonum").equals(otherWO.getString("wonum")) && woMbo.getString("siteid").equals(otherWO.getString("siteid")) && otherWO.isModified()) {
                        woMbo = otherWO;
                    }
                }
            }
        }
        if (woMbo == null) {
            return;
        }
        if (!this.useIntegration(woMbo, "RCWO") && this.toBeAdded()) {
            final String issueType = this.getTranslator().toInternalString("ISSUETYP", this.getString("issuetype"));
            if (this.isMisclReceipt() || issueType.equalsIgnoreCase("RETURN") || issueType.equalsIgnoreCase("VOIDRECEIPT")) {
                woMbo.incrActMatCost(Math.abs(this.getDouble("loadedcost")) * -1.0, this.getBoolean("outside"));
            }
            else if (woMbo.hasChildren()) {
                final SqlFormat sqf = new SqlFormat(this, "parent = :1 and siteid = :siteid");
                sqf.setObject(1, "WORKORDER", "parent", woMbo.getString("wonum"));
                final WOSetRemote childSet = (WOSetRemote)woMbo.getMboSet("$childworkorders", "workorder", sqf.format());
                if (!childSet.isEmpty()) {
                    childMbo = (WORemote)childSet.getMbo(0);
                    if (childMbo.getString("wonum").equalsIgnoreCase(poLineMbo.getString("refwo"))) {
                        childMbo.incrActMatCost(this.getDouble("loadedcost"), this.getBoolean("outside"));
                    }
                    else {
                        woMbo.incrActMatCost(this.getDouble("loadedcost"), this.getBoolean("outside"));
                    }
                }
                else {
                    woMbo.incrActMatCost(this.getDouble("loadedcost"), this.getBoolean("outside"));
                }
            }
            else {
                woMbo.incrActMatCost(this.getDouble("loadedcost"), this.getBoolean("outside"));
            }
            if (woMbo.hasParents()) {
                final WO parentWO = ((WO)woMbo).getParentMbo();
                final MboSetRemote thisSet = this.getThisMboSet();
                boolean parentInSet = false;
                int i = 0;
                while (true) {
                    final MatRecTrans matRec = (MatRecTrans)thisSet.getMbo(i);
                    if (matRec == null) {
                        break;
                    }
                    if (parentWO != null && matRec.getString("refwo").equalsIgnoreCase(parentWO.getString("wonum"))) {
                        parentInSet = true;
                        break;
                    }
                    ++i;
                }
                if (!parentInSet) {
                    woMbo.haveReceivedDirectIssue(poMbo, poLineMbo);
                }
            }
            else {
                woMbo.haveReceivedDirectIssue(poMbo, poLineMbo);
            }
        }
    }
    
    private void issueSpareParts(final MatUseTrans matUseTrans) throws MXException, RemoteException {
        final String assetnum = this.getString("assetnum");
        final String siteid = this.getString("siteid");
        final String itemnum = this.getString("itemnum");
        final MatRecTransSet matSet = (MatRecTransSet)this.getThisMboSet();
        if (!matSet.sparePartExistsInHashTable(assetnum, itemnum)) {
            final ItemSetRemote itemSet = (ItemSetRemote)this.getMboSet("ITEM");
            final ItemRemote itemMbo = (ItemRemote)itemSet.getMbo(0);
            if (itemMbo.canSparePartAutoAdd() && !itemMbo.sparePartExists(assetnum, siteid)) {
                itemMbo.addSparePart(assetnum, siteid);
                matSet.storeNewSparePartKeys(assetnum, itemnum);
                matUseTrans.setValue("sparepartadded", true);
            }
        }
    }
    
    @Override
    public void addRotatingAsset(final String assetnum) throws MXException, RemoteException {
        this.rotAssetsVector.addElement(assetnum);
    }
    
    private void rotatingAssetFromReceiving(final MboRemote assetInput) throws MXException, RemoteException {
        AssetRemote asset = null;
        if (!assetInput.isNull("assetid")) {
            asset = (AssetRemote)assetInput.getMboSet("$FromAsset", "ASSET", "assetuid=:assetid").getMbo(0);
        }
        else {
            final MboSetRemote assetSet = this.getMboSet("ROTASSET");
            if (!assetSet.isEmpty()) {
                asset = (AssetRemote)assetSet.getMbo(0);
            }
            if (asset == null) {
                final String sql = "assetnum=:1 and siteid=:2";
                final SqlFormat sqf = new SqlFormat(this.getUserInfo(), sql);
                sqf.setObject(1, "ASSET", "ASSETNUM", assetInput.getString("assetnum"));
                sqf.setObject(2, "ASSET", "SITEID", this.getString("positeid"));
                final MboSetRemote oneMoreAssetSet = assetInput.getMboSet("CROSSSITEROTASSET", "ASSET", sqf.format());
                asset = (AssetRemote)oneMoreAssetSet.getMbo(0);
            }
        }
        if (asset == null) {
            return;
        }
        if (this.isNull("ponum") || this.getPOLine() == null) {
            asset.setValue("newsite", this.getString("SiteId"), 11L);
            asset.setValue("newlocation", assetInput.getString("location"), 11L);
        }
        else {
            asset.setValue("newsite", this.getPOLine().getString("toSiteId"), 11L);
            asset.setValue("newlocation", this.getPOLine().getString("storeloc"), 11L);
        }
        asset.setValue("newassetnum", assetInput.getString("assetnum"), 11L);
        if (!this.isNull("shipmentnum") && !this.getString("shipmentnum").equalsIgnoreCase("")) {
            final SqlFormat sqf2 = new SqlFormat(assetInput, "assetnum = :assetnum and siteid = :1");
            sqf2.setObject(1, "SITE", "siteid", this.getString("newsite"));
            final MboSetRemote newAssetSet = assetInput.getMboSet("$TOASSETBYROTASSETNUM" + assetInput.getString("assetnum"), "ASSET", sqf2.format());
            if (!newAssetSet.isEmpty()) {
                final String[] params = { assetInput.getString("assetnum"), this.getString("siteid") };
                throw new MXApplicationException("inventory", "blankNewAssetNum", params);
            }
        }
        asset.setValue("status", this.getTranslator().toExternalDefaultValue("LOCASSETSTATUS", "NOT READY", this), 11L);
        final String memo = "";
        if (!this.getString("orgid").equalsIgnoreCase(asset.getString("orgid")) && this.isNull("ponum")) {
            asset.moveAssetWithinInventoryAcrossOrgFromHolding(assetInput.getString("location"), memo, this.getDate("actualdate"), this.getString("tobin"), this.getString("orgid"), this.getString("glcreditacct"), this.getString("gldebitacct"), this.getString("matrectransid"));
        }
        else if (this.isNull("ponum") || this.getPOLine() == null) {
            asset.moveAssetWithinInventory(assetInput.getString("location"), memo, this.getDate("actualdate"), this.getString("tobin"), null, this.getString("glcreditacct"), this.getString("gldebitacct"), this.getString("matrectransid"));
        }
        else {
            asset.moveAssetWithinInventory(this.getPOLine().getString("storeloc"), memo, this.getDate("actualdate"), this.getString("tobin"), this.getString("ponum"), this.getPOLine().getString("glcreditacct"), this.getString("gldebitacct"), this.getString("matrectransid"));
        }
    }
    
    private void rotatingShipTransferAssetFromReceiving(final MboRemote shipTransferAsset) throws MXException, RemoteException {
        AssetRemote asset = null;
        if (!shipTransferAsset.isNull("rotassetnum") && shipTransferAsset.getString("siteid").equalsIgnoreCase(shipTransferAsset.getString("fromsiteid"))) {
            final SqlFormat sqf1 = new SqlFormat(this, "assetnum=:1 and siteid=:2");
            sqf1.setObject(1, "ASSET", "ASSETNUM", shipTransferAsset.getString("rotassetnum"));
            sqf1.setObject(2, "ASSET", "SITEID", shipTransferAsset.getString("siteid"));
            asset = (AssetRemote)((MboSet)((Mbo)shipTransferAsset).getThisMboSet()).getSharedMboSet("ASSET", sqf1.format()).getMbo(0);
        }
        else {
            final MboSetRemote assetSet = this.getMboSet("ROTASSET");
            if (!assetSet.isEmpty()) {
                asset = (AssetRemote)assetSet.getMbo(0);
            }
            if (asset == null) {
                final String sql = "assetnum=:1 and siteid=:2";
                final SqlFormat sqf2 = new SqlFormat(this.getUserInfo(), sql);
                sqf2.setObject(1, "ASSET", "ASSETNUM", shipTransferAsset.getString("rotassetnum"));
                sqf2.setObject(2, "ASSET", "SITEID", this.getString("siteid"));
                final MboSetRemote oneMoreAssetSet = shipTransferAsset.getMboSet("CROSSSITEROTASSET", "ASSET", sqf2.format());
                asset = (AssetRemote)oneMoreAssetSet.getMbo(0);
            }
        }
        if (asset == null) {
            return;
        }
        MboRemote owner = this.getOwner();
        if (owner instanceof RotInspection) {
            owner = owner.getOwner();
        }
        asset.getThisMboSet().setOwner(this);
        final SqlFormat sqf3 = new SqlFormat(this, "shipmentid =:1 and shipmentlinenum=:2");
        sqf3.setLong(1, owner.getLong("shipmentid"));
        sqf3.setObject(2, "SHIPMENT", "SHIPMENTNUM", this.getString("shipmentlinenum"));
        final MboRemote shipmentLine = this.getMboSet("$getShipmentLineSet", "SHIPMENTLINE", sqf3.format()).getMbo(0);
        if (this.isInspectionRequired() && this.isShipReject()) {
            asset.setValue("newsite", this.getString("SiteId"), 11L);
            asset.setValue("newlocation", this.getString("tostoreloc"), 11L);
        }
        else {
            asset.setValue("newsite", shipmentLine.getString("SiteId"), 11L);
            asset.setValue("newlocation", shipmentLine.getString("tostoreloc"), 11L);
        }
        asset.setValue("newassetnum", shipTransferAsset.getString("rotassetnum"), 11L);
        asset.setValue("status", this.getTranslator().toExternalDefaultValue("LOCASSETSTATUS", "NOT READY", this), 11L);
        final String memo = "";
        if (this.isNull("ponum")) {
            asset.moveAssetWithinInventory(asset.getString("newlocation"), memo, this.getDate("actualdate"), this.getString("tobin"), null, this.getString("glcreditacct"), this.getString("gldebitacct"), this.getString("matrectransid"));
        }
        else {
            asset.moveAssetWithinInventory(shipmentLine.getString("tostoreloc"), memo, this.getDate("actualdate"), this.getString("tobin"), this.getString("ponum"), this.getString("glcreditacct"), this.getString("gldebitacct"), this.getString("matrectransid"));
        }
    }
    
    private void rotatingAsset() throws MXException, RemoteException {
        if (this.isTransfer() && !this.isNull("fromstoreloc") && !this.isNull("tostoreloc") && !this.willBeHolding() && !this.isNull("itemnum") && this.getBoolean("ITEM.rotating")) {
            final MboSetRemote assetSet = this.getMboSet("ROTASSET");
            final AssetRemote asset = (AssetRemote)assetSet.getMbo(0);
            asset.setValue("newsite", this.getString("SiteId"), 11L);
            asset.setValue("newlocation", this.getString("tostoreloc"), 11L);
            final MboValue newAssetMbv = ((Mbo)asset).getMboValue("newassetnum");
            if (!newAssetMbv.isFlagSet(7L) && (asset.isNull("newassetnum") || asset.getString("newassetnum").equals(asset.getString("assetnum")))) {
                throw new MXApplicationException("po", "assetneeded");
            }
            final String memo = "";
            asset.setValue("newsite", this.getString("SiteId"), 11L);
            asset.setValue("movedby", this.getString("enterby"), 11L);
            asset.setValue("movedate", this.getString("actualdate"), 11L);
            asset.moveAssetWithinInventory(this.getString("tostoreloc"), memo, this.getDate("actualdate"), this.getString("tobin"), this.getString("ponum"), this.getString("glcreditacct"), this.getString("gldebitacct"), this.getString("matrectransid"));
        }
    }
    
    protected void setRotAssetNum(final String assetnum) throws MXException, RemoteException {
        this.rotAssetNum = assetnum;
    }
    
    protected String getRotAssetNum() throws MXException, RemoteException {
        return this.rotAssetNum;
    }
    
    protected double getAvailableQty(final Inventory inv, final InvBalancesRemote invBal) throws MXException, RemoteException {
        if (invBal == null) {
            throw new MXApplicationException("inventory", "noBalanceRecord");
        }
        double avblQty = 0.0;
        if (this.getString("fromstoreloc").equalsIgnoreCase(this.getString("tostoreloc")) && !this.getString("frombin").equalsIgnoreCase("tobin")) {
            avblQty = invBal.getDouble("curbal");
        }
        else {
            avblQty = inv.getDouble("avblBalance");
            if (!this.isNull("requestnum")) {
                avblQty += Math.abs(this.getDouble("quantity"));
            }
            if (avblQty < Math.abs(this.getDouble("quantity"))) {
                if (!this.isNull("requestnum")) {
                    final double reservedQty = this.getDouble("RESERVEDQTY");
                    final double curBal = invBal.getDouble("curbal");
                    if (curBal >= reservedQty || curBal >= Math.abs(this.getDouble("quantity"))) {
                        avblQty = curBal;
                    }
                }
                else {
                    final InvReserve invReserveMbo = (InvReserve)this.getInvReserve("ponum=:ponum and polinenum=:polinenum and siteid = :siteid and itemnum=:itemnum and itemsetid=:itemsetid");
                    if (invReserveMbo != null) {
                        final double reservedQty2 = invReserveMbo.getDouble("reservedqty");
                        avblQty += reservedQty2;
                    }
                }
            }
        }
        return avblQty;
    }
    
    protected double getSumQtyInTheSet() throws MXException, RemoteException {
        double sumQuantity = 0.0;
        final MboSetRemote matrecSet = this.getThisMboSet();
        int i = 0;
        while (true) {
            final MatRecTrans matRec = (MatRecTrans)matrecSet.getMbo(i);
            if (matRec == null) {
                break;
            }
            if (matRec.toBeAdded() && !matRec.toBeDeleted()) {
                if (matRec.isTransfer()) {
                    if (this.getString("itemnum").equals(matRec.getString("itemnum")) && this.getString("fromstoreloc").equals(matRec.getString("fromstoreloc")) && this.getString("fromsiteid").equals(matRec.getString("fromsiteid")) && this.getString("frombin").equals(matRec.getString("frombin")) && this.getString("fromconditioncode").equals(matRec.getString("fromconditioncode")) && this.getString("fromlot").equals(matRec.getString("fromlot"))) {
                        double fromquantity = Math.abs(matRec.getDouble("quantity"));
                        if (this.getDouble("conversion") != 0.0) {
                            fromquantity /= matRec.getDouble("conversion");
                        }
                        sumQuantity += fromquantity;
                    }
                }
            }
            ++i;
        }
        return sumQuantity;
    }
    
    @Override
    public void appValidate() throws MXException, RemoteException {
        if (!this.needToExecuteAppValidate) {
            return;
        }
        final boolean isShipmentReceipt = this.getOwner() instanceof ShipmentRemote;
        final PORemote poMbo = this.getPO();
        if (poMbo != null && this.isTransferWithPO() && !poMbo.isPOStatusAPPR() && !poMbo.isPOStatusINPRG()) {
            throw new MXApplicationException("labor", "postatuscheck");
        }
        final MboRemote owner = this.getOwner();
        if (owner != null && owner.isBasedOn("InvUse")) {
            super.appValidate();
            return;
        }
        final POLineRemote poLineMbo = this.getPOLine();
        if (this.isNull("conversion") && !this.isNull("itemnum") && !this.isNull("tostoreloc")) {
            if (this.conversionException == null) {
                throw new MXApplicationException("inventory", "conversionDoesNotExistNullMU", new MXApplicationException("inventory", "conversionDoesNotExistInstruction"));
            }
            if (!this.isNull("ponum") && !this.isNull("polinenum")) {
                final Object[] err = { this.conversionException.params[0], this.conversionException.params[1], this.getString("polinenum") };
                throw new MXApplicationException("inventory", "conversionDoesNotExistPOline", err, new MXApplicationException("inventory", "conversionDoesNotExistInstruction"));
            }
            throw new MXApplicationException("inventory", "conversionDoesNotExist", this.conversionException.params, new MXApplicationException("inventory", "conversionDoesNotExistInstruction"));
        }
        else {
            if (!this.isNull("ponum") && this.isNull("polinenum")) {
                final Object[] params = { this.getMboValue("polinenum").getName() };
                throw new MXApplicationException("system", "null", params);
            }
            if (isShipmentReceipt && this.isNull("shipmentlinenum")) {
                final Object[] params = { this.getMboValue("shipmentlinenum").getName() };
                throw new MXApplicationException("system", "null", params);
            }
            if (!this.toBeAdded() && this.getMboValue("qtyheld").isModified()) {
                return;
            }
            super.appValidate();
            if (this.isReceipt() && this.getDouble("quantity") == 0.0) {
                throw new MXApplicationException("dateselector", "zerorepeat");
            }
            final FinancialServiceRemote fsr = (FinancialServiceRemote)MXServer.getMXServer().lookup("FINANCIAL");
            if (fsr.glRequiredForTrans(this.getUserInfo(), this.getString("orgid")) && (this.isNull("gldebitacct") || this.isNull("glcreditacct"))) {
                throw new MXApplicationException("financial", "GLRequiredForTrans");
            }
            if (this.isReceipt() && this.isNull("gldebitacct") && this.isHolding()) {
                MboRemote holdingLoc = null;
                holdingLoc = this.getHoldingLocationForSite(this.getString("siteid"));
                if (holdingLoc != null && holdingLoc.isNull("glaccount") && !this.getMboServer().getMaxVar().getBoolean("DISABLEGLSWITCH", this.getOrgSiteForMaxvar("DISABLEGLSWITCH"))) {
                    throw new MXApplicationException("financial", "GLRequiredForHolding");
                }
            }
            if (this.isTransfer()) {
                if (!this.getBoolean("issue") && this.isNull("toStoreLoc") && this.isNull("courier") && !this.getMboValue("courier").isRequired()) {
                    throw new MXApplicationException("inventory", "transferNoStoreRoom");
                }
                if (!this.getBoolean("issue") && this.isNull("courier")) {
                    final String fromLocation = this.getString("fromstoreloc");
                    final String fromBin = this.getString("frombin");
                    final String fromLot = this.getString("fromlot");
                    final String fromCondition = this.getString("fromconditioncode");
                    final String fromSiteID = this.getString("fromsiteid");
                    final String toLocation = this.getString("tostoreloc");
                    final String toBin = this.getString("tobin");
                    final String toLot = this.getString("tolot");
                    final String toCondition = this.getString("conditioncode");
                    final String toSiteID = this.getString("siteid");
                    if (fromLocation.equals(toLocation) && fromBin.equals(toBin) && fromLot.equals(toLot) && fromCondition.equals(toCondition) && fromSiteID.equals(toSiteID)) {
                        throw new MXApplicationException("inventory", "transferToIdentical");
                    }
                }
                if (!this.willBeHolding()) {
                    final boolean isWasset = this.getReceiptStatus().equalsIgnoreCase("WASSET");
                    if (!this.isNull("itemnum") && this.getBoolean("ITEM.rotating") && !isWasset && this.isNull("rotassetnum")) {
                        throw new MXApplicationException("inventory", "matusetransNullRotassetnum");
                    }
                }
                if (!this.transferFromNonInventory) {
                    if (this.getMboServer().getMaxVar().getBoolean("GLREQUIREDFORTRANS", this.getString("orgid")) && this.isNull("GLCREDITACCT")) {
                        throw new MXApplicationException("inventory", "glcreditRequired");
                    }
                    if (this.getDouble("actualcost") == 0.0) {
                        this.setValue("actualcost", this.getDouble("unitcost"), 2L);
                    }
                }
                final MboRemote mboowner = this.getOwner();
                if (mboowner != null && (!(mboowner instanceof PORemote) || this.isNull("courier")) && ((this.isFromStore() && this.isNull("courier")) || (this.isFromStore() && !this.isNull("courier") && this.isNull("tostoreloc")) || this.isFromCourierOrLabor())) {
                    if (!this.isNull("rotassetnum")) {
                        final MboSetRemote assetSet = this.getMboSet("ROTASSET");
                        if (!assetSet.isEmpty()) {
                            final AssetRemote asset = (AssetRemote)assetSet.getMbo(0);
                            if (asset != null && !this.getString("frombin").equalsIgnoreCase(asset.getString("binnum"))) {
                                final Object[] param = { "Item/Rotating Asset/From Bin", "combination" };
                                throw new MXApplicationException("commlog", "InvalidTemplateId", param);
                            }
                        }
                    }
                    final Inventory inv = (Inventory)this.validateInventory(this.getString("fromstoreloc"), this.getString("fromsiteid"));
                    final InvBalancesRemote invBal = inv.getInvBalanceRecord(this.getString("frombin"), this.getString("fromlot"), this.getString("fromconditioncode"));
                    final double avblQty = this.getAvailableQty(inv, invBal);
                    try {
                        if (!this.requestFulfilled && this.getCheckNegBalance()) {
                            final InventoryService intServ = (InventoryService)((AppService)this.getMboServer()).getMXServer().lookup("INVENTORY");
                            final boolean isWasset2 = this.getReceiptStatus().equalsIgnoreCase("WASSET");
                            final double fromquantity = this.getSumQtyInTheSet();
                            if (!isWasset2) {
                                intServ.canGoNegative(this.getUserInfo(), fromquantity, invBal.getDouble("curbal"), avblQty, this);
                            }
                            if (inv.getCostType().equals("LIFO") || inv.getCostType().equals("FIFO")) {
                                final MboSetRemote invLifoFifoCostSet = inv.getInvLifoFifoCostRecordSet(this.getString("conditionCode"));
                                final double lifoFifoQuantity = invLifoFifoCostSet.sum("quantity");
                                if (lifoFifoQuantity < fromquantity) {
                                    throw new MXApplicationException("inventory", "missingQuantity");
                                }
                            }
                        }
                    }
                    catch (MXException mxe) {
                        this.fromInvBalUpdated = false;
                        this.toInvBalUpdated = false;
                        this.useInitialCurBal = true;
                        throw mxe;
                    }
                    this.useInitialCurBal = false;
                }
            }
            if (this.isReceipt() && this.getDouble("quantity") < 0.0 && this.isStore()) {
                final Inventory inv2 = (Inventory)this.validateInventory(this.getString("tostoreloc"), this.getString("siteid"));
                final InvBalancesRemote invBal2 = inv2.getInvBalanceRecord(this.getString("tobin"), this.getString("tolot"), this.getString("conditioncode"));
                final double avblQty2 = this.getAvailableQty(inv2, invBal2);
                try {
                    if (!this.requestFulfilled) {
                        final InventoryService intServ2 = (InventoryService)((AppService)this.getMboServer()).getMXServer().lookup("INVENTORY");
                        double fromquantity2 = Math.abs(this.getDouble("quantity"));
                        if (this.getDouble("conversion") != 0.0) {
                            fromquantity2 /= this.getDouble("conversion");
                        }
                        intServ2.canGoNegative(this.getUserInfo(), fromquantity2, invBal2.getDouble("curbal"), avblQty2, this);
                    }
                }
                catch (MXException mxe2) {
                    this.fromInvBalUpdated = false;
                    this.toInvBalUpdated = false;
                    this.useInitialCurBal = true;
                    throw mxe2;
                }
                this.useInitialCurBal = false;
            }
            if (this.isMisclReceipt()) {
                if (this.isNull("assetnum") && this.isNull("location") && this.isNull("wonum") && this.isNull("glcreditacct")) {
                    throw new MXApplicationException("inventory", "miscReceiptIssue");
                }
                if (this.isNull("toStoreloc")) {
                    throw new MXApplicationException("inventory", "miscReceiptStoreLoc");
                }
            }
            final MboRemote itemRemote = this.getMboSet("ITEM").getMbo(0);
            if (itemRemote != null) {
                if (this.getDouble("quantity") != 0.0 && ((ItemRemote)itemRemote).isLotted()) {
                    if (poLineMbo != null && !poLineMbo.getBoolean("issue") && this.getOwner() != null && !this.getOwner().isBasedOn("InvUse")) {
                        if (!this.getReceiptStatus().equals("WINSP") && (this.getString("tobin").equals("") || this.getString("tolot").equals(""))) {
                            throw new MXApplicationException("inventory", "toBinAndToLotEmpty");
                        }
                    }
                    else if (this.getString("tolot").equals("")) {
                        throw new MXApplicationException("inventory", "toBinAndToLotEmpty");
                    }
                }
                if (((ItemRemote)itemRemote).isConditionEnabled() && this.isNull("conditioncode")) {
                    final Object[] param2 = { itemRemote.getString("itemnum") };
                    throw new MXApplicationException("inventory", "noConditionCode", param2);
                }
            }
            final String issueType = this.getTranslator().toInternalString("ISSUETYP", this.getString("issuetype"));
            if (this.toBeAdded()) {
                if (this.getDouble("RECEIPTQUANTITY") <= 0.0 && issueType.equalsIgnoreCase("RECEIPT") && poLineMbo.getDouble("orderqty") > 0.0) {
                    throw new MXApplicationException("po", "nonNegativeQty");
                }
                if (this.getDouble("quantity") < 0.0 && issueType.equalsIgnoreCase("RECEIPT")) {
                    this.setValue("status", "!COMP!", 11L);
                    if (poLineMbo != null && poLineMbo.getDouble("orderqty") > 0.0) {
                        this.setValue("receiptref", this.getOwner().getMboSet("RECEIPTTYPEMATREC").getMbo(0).getLong("matrectransid"), 11L);
                        this.setValue("issuetype", this.getTranslator().toExternalDefaultValue("ISSUETYP", "RETURN", this), 11L);
                    }
                }
                else if (this.getDouble("quantity") > 0.0 && issueType.equalsIgnoreCase("RETURN")) {
                    this.setValue("issuetype", this.getTranslator().toExternalDefaultValue("ISSUETYP", "RECEIPT", this), 11L);
                }
                if (this.getTranslator().toInternalString("ISSUETYP", this.getString("issuetype")).equalsIgnoreCase("RETURN") && !this.isReject() && poLineMbo != null) {
                    this.setValue("tostoreloc", poLineMbo.getString("storeloc"), 11L);
                }
                if (issueType.equalsIgnoreCase("RECEIPT") && !this.isNull("status") && !this.isHolding() && !this.willBeHolding() && poLineMbo != null && !poLineMbo.getString("gldebitacct").equalsIgnoreCase(this.getString("gldebitacct"))) {
                    throw new MXApplicationException("inventory", "cannotChangeGLONReceipt");
                }
                if (issueType.equalsIgnoreCase("POCOST") && this.isNull("issueto") && owner != null && owner.getString("issueto") != null) {
                    this.setValue("issueto", owner.getString("issueto"), 11L);
                }
            }
        }
    }
    
    boolean isTransferFromNonInventory() throws MXException, RemoteException {
        return this.transferFromNonInventory;
    }
    
    public void save() throws MXException, RemoteException {
        cust.component.Logger.Log("MatRecTrans.save");
        if (this.toBeDeleted()) {
            return;
        }
        final MboRemote owner = this.getOwner();
        if (this.recordUpdated && owner != null && owner.isBasedOn("Locations") && this.fromInvBalUpdated && this.getMboValue("receiptquantity").getPreviousValue().asDouble() == this.getDouble("receiptquantity")) {
            super.save();
            return;
        }
        boolean inIssuesAndTransfers = false;
        if (owner != null && owner.isBasedOn("Locations")) {
            final MboSetRemote locSet = owner.getThisMboSet();
            final String appName = locSet.getApp();
            if (appName != null && !appName.equals("")) {
                final MboRemote ownersOwner = owner.getOwner();
                if (ownersOwner == null) {
                    inIssuesAndTransfers = true;
                }
            }
        }
        if (this.recordUpdated && inIssuesAndTransfers && this.isTransfer() && owner != null && owner.isBasedOn("Locations") && this.getMboValue("receiptquantity").getPreviousValue().asDouble() != this.getDouble("receiptquantity")) {
            this.fromInvBalUpdated = false;
            this.toInvBalUpdated = false;
            this.useInitialCurBal = true;
        }
        if (this.isKitting()) {
            super.save();
            return;
        }
        double receiptConversion = 0.0;
        if (!this.isNull("conversion")) {
            receiptConversion = this.getDouble("conversion");
        }
        if (receiptConversion == 0.0) {
            receiptConversion = 1.0;
        }
        final PORemote poMbo = this.getPO();
        POLineRemote poLineMbo = this.getPOLine();
        final ItemRemote item = (ItemRemote)this.getMboSet("ITEM").getMbo(0);
        if (item != null && item.isRotating() && this.getString("siteid").equalsIgnoreCase(this.getString("fromsiteid")) && this.getReceiptStatus().equals("COMP") && !this.isNull("invuseid")) {
            ((MatRecTransSet)this.getThisMboSet()).setLineNumAssetMap(this.getString("shipmentlinenum"), this.getString("rotassetnum"));
            if (!this.isInspectionRequired() && this.isShipReceipt()) {
                this.approveShipReceipt();
            }
            else if (this.isInspectionRequired() && (this.isShipReject() || this.isTransfer())) {
                final MboRemote receipt = this.getReceiptForThisReturn();
                if (receipt != null && this.getString("siteid").equalsIgnoreCase(receipt.getString("fromsiteid"))) {
                    this.approveShipReceipt();
                }
            }
        }
        if (this.isTransfer()) {
            cust.component.Logger.Log("---MARKER 2---");
            final MboRemote mboowner = this.getOwner();
            if (mboowner instanceof LocationRemote || mboowner.isBasedOn("InvUse")) {
                if (item != null && mboowner != null && this.toBeAdded() && item.isKit() && !this.allKitComponentsAreInTransferToStore()) {
                    final MXApplicationYesNoCancelException mxync = new MXApplicationYesNoCancelException("InvIssueAddKitComponentsInteractId", "item", "componentsNotInStore");
                    mxync.postUserInput(MXServer.getMXServer(), -1, this.getUserInfo());
                    throw new MXApplicationException("inventory", "kittransnotsaved");
                }
                if (!this.toBeDeleted() && !mboowner.isBasedOn("InvUse")) {
                    if (!mboowner.getMboSet("MATRECTRANSOUT").isEmpty()) {
                        if (this.isNull("courier") && this.isNull("tostoreloc")) {
                            throw new MXApplicationException("inventory", "courierAndToStorelocNull");
                        }
                        final SiteServiceRemote siteService = (SiteServiceRemote)MXServer.getMXServer().lookup("SITE");
                        if (poMbo != null) {
                            final String storeLocOrg = siteService.getOrgForSite(poMbo.getString("storelocsiteid"), this.getUserInfo());
                            final String toOrg = siteService.getOrgForSite(poLineMbo.getString("tositeid"), this.getUserInfo());
                            if (poMbo.getBoolean("internal") && (!storeLocOrg.equalsIgnoreCase(toOrg) || (!poMbo.getString("storelocsiteid").equalsIgnoreCase(poLineMbo.getString("tositeid")) && (this.isInspectionRequired() || item.isRotating()))) && this.isNull("courier")) {
                                throw new MXApplicationException("inventory", "courierNullCrossSite");
                            }
                        }
                    }
                    if (!mboowner.getMboSet("MATRECTRANSIN").isEmpty() && this.isNull("courier") && this.isNull("fromstoreloc")) {
                        throw new MXApplicationException("inventory", "courierAndFromStorelocNull");
                    }
                }
            }
        }
        if (!this.toBeAdded() && this.getMboValue("qtyheld").isModified()) {
            super.save();
            return;
        }
        this.updateInventoryCostAndBalances();
        if (owner != null && owner instanceof LocationRemote && this.getThisMboSet() == owner.getMboSet("MATRECTRANSMOVEIN")) {
            super.save();
            return;
        }
        if (this.isTransfer()) {
            cust.component.Logger.Log("---MARKER 3---");
            final MboRemote mboowner = this.getOwner();
            if (owner instanceof LocationRemote) {
                if (!mboowner.getMboSet("MATRECTRANSOUT").isEmpty()) {
                    if (this.getMboValue("courier").isNull() && !this.getMboValue("toStoreloc").isNull()) {
                        this.setValue("status", this.getTranslator().toExternalDefaultValue("RECEIPTSTATUS", "COMP", this), 11L);
                    }
                    else if (!this.getMboValue("courier").isNull() && this.getMboValue("toStoreloc").isNull()) {
                        this.setValue("status", this.getTranslator().toExternalDefaultValue("RECEIPTSTATUS", "TRANSFER", this), 11L);
                        if (!this.isNull("itemnum") && this.getBoolean("ITEM.rotating")) {
                            final MboSetRemote assetSet = this.getMboSet("ROTASSET");
                            if (!assetSet.isEmpty()) {
                                final AssetRemote asset = (AssetRemote)assetSet.getMbo(0);
                                if (!asset.getString("siteid").equals(this.getString("siteid"))) {
                                    final String internalStatus = this.getTranslator().toInternalString("LOCASSETSTATUS", asset.getString("status"));
                                    if (!internalStatus.equals("DECOMMISSIONED")) {
                                        final String newStatus = this.getTranslator().toExternalDefaultValue("LOCASSETSTATUS", "DECOMMISSIONED", asset);
                                        asset.changeStatus(newStatus, true, true, true, true);
                                    }
                                }
                            }
                        }
                    }
                }
                if (!mboowner.getMboSet("MATRECTRANSIN").isEmpty()) {
                    if (this.getMboValue("courier").isNull() && !this.getMboValue("fromStoreloc").isNull()) {
                        this.setValue("status", this.getTranslator().toExternalDefaultValue("RECEIPTSTATUS", "COMP", this), 11L);
                    }
                    else if (!this.getMboValue("courier").isNull()) {
                        this.setValue("status", this.getTranslator().toExternalDefaultValue("RECEIPTSTATUS", "TRANSFER", this), 11L);
                    }
                }
            }
        }
        if (this.toBeAdded()) {
            if (poMbo != null && this.isNull("invoicenum") && this.useIntegration(poMbo, "RC")) {
                throw new MXApplicationException("inventory", "mxcollabRC");
            }
            final String issueType = this.getTranslator().toInternalString("ISSUETYP", this.getString("issuetype"));
            if (poMbo != null && poLineMbo == null && this.isTransfer()) {
                final SqlFormat sqf = new SqlFormat(this, "ponum = :ponum and polinenum = :polinenum and siteid = :siteid");
                final MboSetRemote polineSet = ((MboSet)this.getThisMboSet()).getSharedMboSet("POLINE", sqf.format());
                if (owner != null && owner.getName().equalsIgnoreCase("PO")) {
                    polineSet.setOwner(owner);
                }
                poLineMbo = (POLineRemote)polineSet.getMbo(0);
            }
            if (((poLineMbo != null && poLineMbo.getString("storeloc").equals(this.getString("tostoreloc"))) || (issueType.equalsIgnoreCase("RETURN") && !this.isReject())) && !this.useIntegration(poMbo, "RCPO")) {
                final double exchangeRate = this.getDouble("exchangerate");
                double loadedCost = this.getDouble("loadedcost");
                double currencyLoadedCost = this.getDouble("loadedcost");
                if (exchangeRate != 0.0 && exchangeRate != 1.0) {
                    loadedCost = this.getExtendedLoadedCost();
                }
                if (exchangeRate != 0.0 && exchangeRate != 1.0) {
                    currencyLoadedCost = MXMath.divide(loadedCost, exchangeRate);
                }
                if (issueType.equalsIgnoreCase("RECEIPT") || issueType.equalsIgnoreCase("SHIPRECEIPT") || issueType.equalsIgnoreCase("RETURN") || issueType.equalsIgnoreCase("VOIDRECEIPT") || issueType.equalsIgnoreCase("SHIPRETURN") || issueType.equalsIgnoreCase("VOIDSHIPRECEIPT") || issueType.equalsIgnoreCase("INVOICE") || (this.isTransfer() && poMbo != null)) {
                    if (!this.getPOLineUpdated() && ((!this.isNull("itemnum") && !this.getBoolean("ITEM.rotating") && !this.isInspectionRequired()) || this.getTranslator().toInternalString("LINETYPE", this.getString("linetype")).equalsIgnoreCase("MATERIAL") || (!issueType.equalsIgnoreCase("VOIDRECEIPT") && !issueType.equalsIgnoreCase("VOIDSHIPRECEIPT")))) {
                        final String lineOrLoaded = this.useLineOrLoadedCost();
                        if (issueType.equalsIgnoreCase("INVOICE")) {
                            MXServer.getBulletinBoard().post("matrectrans.UPDATE_RECEIVED_UNITCOST_FROM_VARIANCE", this.getUserInfo());
                            if (lineOrLoaded.equalsIgnoreCase("LINECOST")) {
                                poLineMbo.updatePOPOLine(0.0, 0.0, this.getDouble("currencylinecost"), poMbo, receiptConversion);
                            }
                            else {
                                poLineMbo.updatePOPOLine(0.0, 0.0, currencyLoadedCost, poMbo, receiptConversion);
                            }
                            MXServer.getBulletinBoard().remove("matrectrans.UPDATE_RECEIVED_UNITCOST_FROM_VARIANCE", this.getUserInfo());
                        }
                        else if (issueType.equalsIgnoreCase("SHIPRETURN") && this.willBeHolding()) {
                            poLineMbo.updatePOPOLine(0.0, this.getDouble("receiptquantity") * -1.0, 0.0, poMbo, receiptConversion);
                        }
                        else if (lineOrLoaded.equalsIgnoreCase("LINECOST")) {
                            poLineMbo.updatePOPOLine(this.getDouble("receiptquantity"), this.getDouble("REJECTQTY"), this.getDouble("currencylinecost"), poMbo, receiptConversion);
                        }
                        else {
                            poLineMbo.updatePOPOLine(this.getDouble("receiptquantity"), this.getDouble("REJECTQTY"), currencyLoadedCost, poMbo, receiptConversion);
                        }
                        this.setPOLineUpdated();
                    }
                    if ((this.getReceiptStatus().equals("COMP") && !issueType.equalsIgnoreCase("INVOICE")) || (poMbo != null && this.isTransfer() && this.getString("fromstoreloc") != null && this.getString("tostoreloc") != null && (this.getReceiptStatus().equals("") || this.getReceiptStatus().equals("TRANSFER")))) {
                        poLineMbo.receiptComplete();
                        poMbo.determineReceiptStatus(poLineMbo);
                    }
                }
            }
            if (poLineMbo != null && this.isTransfer() && this.courierLaborMatRec != null) {
                this.courierLaborMatRec.setValue("qtyheld", this.courierLaborMatRec.getDouble("qtyheld") - this.getDouble("quantity"), 2L);
            }
            if (this.isVoidShipReceipt()) {
                final SqlFormat sqf = new SqlFormat(this, "matrectransid=:1 and status in (select value from synonymdomain where domainid='RECEIPTSTATUS' and maxvalue in ('WINSP','WASSET'))");
                sqf.setLong(1, this.getLong("receiptref"));
                final MboSetRemote receiptSet = ((MboSet)this.getThisMboSet()).getSharedMboSet("MATRECTRANS", sqf.format());
                int j = 0;
                while (true) {
                    final MboRemote receiptRemote = receiptSet.getMbo(j);
                    if (receiptRemote == null) {
                        break;
                    }
                    receiptRemote.setValue("status", "!COMP!", 2L);
                    ++j;
                }
            }
            if (((owner instanceof ShipmentRemote || owner instanceof RotInspection) && ((this.isShipReceipt() && this.getReceiptStatus().equals("COMP")) || (this.isTransfer() && this.getReceiptStatus().equals("COMP")))) || this.isShipReturn()) {
                final SqlFormat sqf2 = new SqlFormat(this, "invuseid = :1");
                sqf2.setLong(1, this.getLong("invuseid"));
                final InvUseRemote invUse = (InvUseRemote)((MboSet)this.getThisMboSet()).getSharedMboSet("INVUSE", sqf2.format()).getMbo(0);
                final SqlFormat sqf3 = new SqlFormat(this, "invuselineid = :1");
                sqf3.setLong(1, this.getLong("invuselineid"));
                final InvUseLineRemote invUseLine = (InvUseLineRemote)((MboSet)this.getThisMboSet()).getSharedMboSet("INVUSELINE", sqf3.format()).getMbo(0);
                if (invUse != null && invUseLine != null) {
                    if (this.isShipReceipt() || this.isVoidShipReceipt() || this.isTransfer()) {
                        invUseLine.updateReceivedQty(this.getDouble("receiptquantity"));
                    }
                    else if (this.isShipReturn()) {
                        invUseLine.updateReturnedQty(this.getDouble("receiptquantity") * -1.0);
                        if (owner instanceof RotInspection && poLineMbo != null) {
                            poLineMbo.updatePOPOLine(0.0, this.getDouble("receiptquantity") * -1.0, 0.0, poMbo, receiptConversion);
                        }
                    }
                    invUseLine.updateReceiptsComplete();
                    invUse.updateInvUseReceipts(invUseLine);
                    final HashMap<Long, MboRemote> invUseLineMap = ((InvUse)invUse).getInvUseLineMap();
                    if (invUseLineMap != null) {
                        invUseLineMap.put(invUseLine.getLong("invuselineid"), invUseLine);
                    }
                }
            }
            if (this.isTransfer()) {
                cust.component.Logger.Log("---MARKER 4---");
                if (this.isCourierOrLabor()) {
                    this.setValue("qtyheld", this.getDouble("quantity"), 2L);
                    this.updateRelatedObjects(poMbo, poLineMbo);
                    this.recordUpdated = true;
                    super.save();
                    return;
                }
                if (poLineMbo == null) {
                    this.updateRelatedObjects(poMbo, poLineMbo);
                    this.adjustPhysicalCount();
                    this.recordUpdated = true;
                    super.save();
                    this.createInvoiceOnConsumption();
                    return;
                }
                if (this.willBeHolding()) {
                    this.updateRelatedObjects(poMbo, poLineMbo);
                    this.adjustPhysicalCount();
                    this.recordUpdated = true;
                    super.save();
                    return;
                }
            }
            if (this.isMisclReceipt()) {
                this.updateRelatedObjects(poMbo, poLineMbo);
                super.save();
                return;
            }
            if (this.isVoidReceipt()) {
                final SqlFormat sqf = new SqlFormat(this, "matrectransid=:1 and status in (select value from synonymdomain where domainid='RECEIPTSTATUS' and maxvalue in ('WINSP','WASSET'))");
                sqf.setLong(1, this.getLong("receiptref"));
                final MboSetRemote receiptSet = ((MboSet)this.getThisMboSet()).getSharedMboSet("MATRECTRANS", sqf.format());
                int j = 0;
                while (true) {
                    final MboRemote receiptRemote = receiptSet.getMbo(j);
                    if (receiptRemote == null) {
                        break;
                    }
                    receiptRemote.setValue("status", "!COMP!", 2L);
                    ++j;
                }
            }
        }
        final ReceiptMboSet receiptSet2 = (ReceiptMboSet)this.getThisMboSet();
        receiptSet2.incCurrentReceiptCount();
        final String issueType2 = this.getTranslator().toInternalString("ISSUETYP", this.getString("issuetype"));
        if (poLineMbo != null && !poLineMbo.isInspectionRequired() && this.isModified()) {
            this.updateRelatedObjects(poMbo, poLineMbo);
        }
        else if (poLineMbo != null && poLineMbo.isInspectionRequired()) {
            if (issueType2.equals("RETURN") && !this.isNull("receiptref")) {
                final MatRecTrans receiptMbo = (MatRecTrans)this.getReceiptForThisReturn();
                if (receiptMbo != null && receiptMbo.getReceiptStatus().equals("COMP")) {
                    this.updateRelatedObjects(poMbo, poLineMbo);
                }
            }
            if (issueType2.equals("INVOICE")) {
                this.updateRelatedObjects(poMbo, poLineMbo);
            }
        }
        if (!this.isNull("status") && issueType2.equals("RECEIPT") && this.getTranslator().toInternalString("RECEIPTSTATUS", this.getString("status")).equalsIgnoreCase("COMP") && poLineMbo != null && poLineMbo.getDouble("scheduleid") != 0.0) {
            this.createInvoicesForSchedule();
        }
        if (poMbo != null && poMbo.getBoolean("payonreceipt") && this.isNull("invoicenum") && this.getReceiptStatus().equals("COMP")) {
            this.onlyPayOnReceipt(poMbo, poLineMbo);
        }
        if (poLineMbo != null && !poLineMbo.isInspectionRequired()) {
            this.adjustPhysicalCount();
        }
        super.save();
        this.createInvoiceOnConsumption();
    }
    
    public void createInvoiceOnConsumption() throws MXException, RemoteException {
        final MboRemote owner = this.getOwner();
        if (this.toBeAdded() && MXMath.abs(this.getDouble("quantity")) > 0.0 && owner != null && (owner.isBasedOn("INVUSE") || owner.isBasedOn("INVENTORY")) && (this.isTransfer() || this.isShipTransfer())) {
            final InventoryRemote invMbo = (InventoryRemote)this.getSharedInventory(this.getString("fromstoreloc"), this.getString("fromsiteid"));
            if (invMbo != null && invMbo.isConsignment()) {
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
    }
    
    public void updateInvReserveActualQty(final MboRemote invUseLine) throws MXException, RemoteException {
        final MboRemote invReserveMbo = invUseLine.getMboSet("INVRESERVE").getMbo(0);
        if (invReserveMbo != null) {
            final double qty = invReserveMbo.getDouble("actualqty") - invUseLine.getDouble("returnedqty");
            invReserveMbo.setValue("actualqty", qty, 2L);
        }
    }
    
    @Override
    public MboRemote getReceiptForThisReturn() throws MXException, RemoteException {
        final SqlFormat sqlf = new SqlFormat(this, "matrectransid = :1");
        sqlf.setObject(1, "MATRECTRANS", "RECEIPTREF", this.getString("receiptref"));
        return this.getMboSet("$origMatRec", "MATRECTRANS", sqlf.format()).getMbo(0);
    }
    
    public MboRemote getshipTransferForThisShipReceipt() throws MXException, RemoteException {
        final SqlFormat sqlf = new SqlFormat(this, "matrectransid = :1");
        sqlf.setObject(1, "MATRECTRANS", "RECEIPTREF", this.getString("receiptref"));
        return this.getMboSet("$origMatRec", "MATRECTRANS", sqlf.format()).getMbo(0);
    }
    
    private void updateRelatedObjects(final PORemote poMbo, final POLineRemote poLineMbo) throws MXException, RemoteException {
        final String issueType = this.getTranslator().toInternalString("ISSUETYP", this.getString("issuetype"));
        if (issueType.equalsIgnoreCase("POCOST")) {
            final MboRemote owner = this.getOwner();
            if (owner != null && owner instanceof MatRecTransRemote) {
                this.setValue("actualdate", owner.getString("actualdate"), 11L);
            }
            return;
        }
        if (issueType.equalsIgnoreCase("RECEIPT") || (issueType.equalsIgnoreCase("TRANSFER") && poMbo != null) || issueType.equalsIgnoreCase("MISCLRECEIPT")) {
            if (poLineMbo != null && !this.useIntegration(poMbo, "RCPO")) {
                poLineMbo.receiptComplete();
            }
            if (!this.isNull("mrnum")) {
                this.updateMR(poLineMbo);
            }
        }
        this.rotatingAsset();
        if (((this.getBoolean("issue") && (this.isReceipt() || this.isInvoice() || issueType.equalsIgnoreCase("VOIDRECEIPT") || issueType.equalsIgnoreCase("RETURN"))) || this.isMisclReceipt()) && this.getReceiptStatus().equals("COMP")) {
            if (!this.isReceipt() || this.getDouble("quantity") == 0.0 || this.getDouble("quantity") != this.getDouble("rejectqtydisplay")) {
                final MatUseTransSet matUseSet = (MatUseTransSet)this.getMboSet("$MatUseFromMatRec", "MATUSETRANS", "matrectransid=:matrectransid");
                matUseSet.setOwner(this);
                MatUseTrans matUseTrans = null;
                if (matUseSet.isEmpty()) {
                    matUseTrans = (MatUseTrans)matUseSet.add(2L);
                }
                else {
                    matUseTrans = (MatUseTrans)matUseSet.getMbo(0);
                }
                this.doChargeStore();
                if (!this.isNull("refwo")) {
                    this.updateWorkOrder(poMbo, poLineMbo);
                }
                if (!this.isNull("itemnum") && (!this.isNull("assetnum") || !this.isNull("rotassetnum"))) {
                    this.issueSpareParts(matUseTrans);
                }
            }
        }
        else if (this.isTransfer() && this.getBoolean("issue") && this.willBeHolding() && this.getReceiptStatus().equals("COMP") && !this.isNull("refwo")) {
            this.updateWorkOrder(poMbo, poLineMbo);
        }
        if (!this.getBoolean("issue") || this.isMisclReceipt()) {
            final InventoryRemote invmbo = (InventoryRemote)this.getSharedInventory(this.getString("tostoreloc"), this.getString("siteid"));
            if (this.isCourierOrLabor()) {
                this.setValue("qtyheld", this.getDouble("quantity"), 2L);
            }
            String costMethod = null;
            if (invmbo == null) {
                costMethod = this.getMboServer().getMaxVar().getString("DEFISSUECOST", this.getOrgSiteForMaxvar("DEFISSUECOST"));
            }
            else {
                costMethod = invmbo.getCostType();
            }
            if (costMethod != null && (costMethod.equalsIgnoreCase("STANDARD") || costMethod.equalsIgnoreCase("STDCOST"))) {
                double standardCost = 0.0;
                if (invmbo != null) {
                    standardCost = invmbo.getDefaultIssueCost(this.getString("conditioncode"));
                    if (standardCost != this.getDouble("unitcost")) {
                        this.createStdRecAdj(standardCost);
                    }
                }
            }
        }
    }
    
    private double getExtendedLoadedCost() throws RemoteException, MXException {
        final Cost cost = new Cost();
        double lineCost = 0.0;
        if (this.getMboValue("currencylinecost").getScale() >= this.getMboValue("currencyunitcost").getScale()) {
            if (this.getDouble("exchangerate") < 1.0) {
                lineCost = this.getDouble("currencylinecost") * this.getDouble("exchangerate");
            }
            else {
                lineCost = this.getDouble("currencylinecost") / this.getDouble("exchangerate");
            }
        }
        else {
            lineCost = this.getDouble("currencyunitcost") / this.getDouble("exchangerate") * this.getDouble("quantity");
        }
        final double prorateCost = cost.calcMatRecProrateCost(this);
        final boolean issue = this.getBoolean("issue");
        final Taxes tax = new Taxes(this);
        final double[] taxes = new double[tax.NUMBEROFTAXCODES + 1];
        for (int i = 1; i < taxes.length; ++i) {
            final String whichTaxCode = "tax" + i + "code";
            taxes[i] = tax.calculateTax(lineCost, this.getString(whichTaxCode), i);
            taxes[i] = this.getDouble("tax" + i);
        }
        final double loadedCost = cost.calcLoadedCost(this.getUserInfo(), lineCost, prorateCost, taxes, issue, this.getString("orgid"), this);
        return loadedCost;
    }
    
    @Override
    protected boolean skipCopyField(final MboValueInfo mvi) throws RemoteException, MXException {
        return mvi.getName().equalsIgnoreCase("MATRECTRANSID") || mvi.getName().equalsIgnoreCase("ACTUALDATE") || mvi.getName().equalsIgnoreCase("ORGID") || mvi.getName().equalsIgnoreCase("SITEID") || mvi.getName().equalsIgnoreCase("ENTERBY");
    }
    
    @Override
    public void setAproveAfterCreatingAssets(final boolean flag) throws MXException, RemoteException {
        this.approveAfterCreatingAssets = flag;
    }
    
    @Override
    public boolean getApproveAfterCreatingAssets() throws MXException, RemoteException {
        return this.approveAfterCreatingAssets;
    }
    
    @Override
    public void rejectsForRotatingShipments() throws MXException, RemoteException {
        this.setValue("status", "!COMP!", 11L);
        final MatRecTransRemote matrec = (MatRecTransRemote)this.getThisMboSet().addAtEnd();
        this.copyMatRecTransToMatRecTrans(matrec, "SHIPRETURN");
    }
    
    @Override
    public void transferForRotatingShipments() throws MXException, RemoteException {
        this.setValue("status", "!COMP!", 11L);
        final MatRecTransRemote matrec = (MatRecTransRemote)this.getThisMboSet().addAtEnd();
        this.copyMatRecTransToMatRecTrans(matrec, "TRANSFER");
    }
    
    @Override
    public void approveShipReceipt() throws MXException, RemoteException {
        final SqlFormat sql = new SqlFormat("1=2");
        MboSetRemote assetSetRemote = null;
        final SqlFormat sqf = new SqlFormat(this, "shipmentnum =:1 and shipmentlinenum =:2 and issuetype in (select value from synonymdomain where domainid='ISSUETYP' and maxvalue=:3) and siteid = :4");
        sqf.setObject(1, "MATRECTRANS", "SHIPMENTNUM", this.getString("shipmentnum"));
        sqf.setObject(2, "MATRECTRANS", "SHIPMENTLINENUM", this.getString("shipmentlinenum"));
        sqf.setObject(3, "MATRECTRANS", "ISSUETYPE", "SHIPTRANSFER");
        sqf.setObject(4, "MATRECTRANS", "SITEID", this.getString("siteid"));
        final MboSetRemote shipTransferAssetSet = this.getMboSet("$getShipTransferSet", "MATRECTRANS", sqf.format());
        assetSetRemote = ((MboSet)this.getThisMboSet()).getSharedMboSet("ASSET", sql.format());
        int i = 0;
        while (true) {
            final MboRemote shipTransferAsset = shipTransferAssetSet.getMbo(i);
            if (shipTransferAsset == null) {
                break;
            }
            if (!shipTransferAsset.isNull("rotassetnum")) {
                if (!this.isShipReject() || !this.isInspectionRequired()) {
                    this.rotatingShipTransferAssetFromReceiving(shipTransferAsset);
                }
                if (!this.getString("siteid").equalsIgnoreCase(this.getString("fromsiteid"))) {
                    this.approve(((AppService)this.getMboServer()).getMXServer().getDate());
                }
            }
            ++i;
        }
    }
    
    @Override
    public void approve(final MboSetRemote assetInputSet) throws MXException, RemoteException {
        cust.component.Logger.Log("MatRecTrans.approve2");
        SqlFormat sql = new SqlFormat("1=2");
        boolean canAppr = true;
        boolean isLargeApprovalAmt = false;
        boolean inVendorPO = false;
        int countOfAssetsAdded = 0;
        double lastLoadedCost = 0.0;
        String lastItemNum = "";
        String lastIssueType = "";
        String lastFinancialPeriod = "";
        boolean firstAssetAdded = false;
        boolean firstAssetAddedCheckLoc = false;
        if (this.getDouble("quantity") >= 1000.0 && (this.getUserInfo().isInteractive() || !((AssetInputSet)assetInputSet).getUsingUpdateReceipt())) {
            isLargeApprovalAmt = true;
        }
        int j = 0;
        MboSetRemote assetSetRemote = null;
        if (isLargeApprovalAmt) {
            assetSetRemote = this.getMboServer().getMboSet("ASSET", this.getUserInfo());
            assetSetRemote.setWhere("1=2");
            assetSetRemote.reset();
            assetSetRemote.setOwner(this);
            countOfAssetsAdded = 0;
            inVendorPO = false;
        }
        else {
            assetSetRemote = ((MboSet)this.getThisMboSet()).getSharedMboSet("ASSET", sql.format());
            assetSetRemote.setOwner(this);
        }
        String parent = null;
        if (!this.isNull("itemnum") && !this.isReturn()) {
            final MboRemote item = this.getMboSet("ITEM").getMbo(0);
            if (item.getBoolean("attachonissue")) {
                parent = this.getString("rotassetnum");
            }
            item.getThisMboSet().reset();
        }
        int i = 0;
        while (true) {
            final MboRemote assetInput = assetInputSet.getMbo(i);
            if (assetInput == null) {
                if (isLargeApprovalAmt && inVendorPO && !assetSetRemote.isEmpty()) {
                    assetSetRemote.save();
                }
                if (canAppr && this.approveAfterCreatingAssets && !((AssetInputSet)assetInputSet).getUsingUpdateReceipt()) {
                    this.approve(((AppService)this.getMboServer()).getMXServer().getDate());
                }
                else if (!this.getUserInfo().isInteractive() && ((AssetInputSet)assetInputSet).getUsingUpdateReceipt()) {
                    this.setValue("status", "!COMP!", 11L);
                    final SqlFormat sqlf = new SqlFormat(this, "receiptref = :matrectransid and externalrefid = :externalrefid");
                    final MboSetRemote transSet = this.getMboSet("$trandMatRecForMea", "MATRECTRANS", sqlf.format());
                    final MboRemote transMbo = transSet.getMbo(0);
                    transMbo.setValue("quantity", this.getDouble("quantity"), 3L);
                    transMbo.setValue("receiptquantity", this.getDouble("quantity"), 11L);
                    transMbo.setValue("currencylinecost", this.getDouble("currencylinecost"), 11L);
                    transMbo.setValue("linecost", this.getDouble("linecost"), 11L);
                    transMbo.setValue("loadedcost", this.getDouble("loadedcost"), 11L);
                    cust.component.Logger.Log("linecost2#2");
                    transMbo.setValue("linecost2", this.getDouble("linecost2"), 11L);
                    final POLineRemote poLineMbo = this.getPOLine();
                    final PORemote poMbo = this.getPO();
                    poLineMbo.updatePOPOLine(this.getDouble("receiptquantity"), this.getDouble("REJECTQTY"), this.getDouble("currencylinecost"), poMbo, poLineMbo.getDouble("conversion"));
                    poLineMbo.receiptComplete();
                    poMbo.determineReceiptStatus(poLineMbo);
                }
                return;
            }
            if (!assetInput.isNull("assetnum")) {
                if (assetInput.toBeDeleted() && this.getString("matrectransid").equalsIgnoreCase(assetInput.getString("matrectransid"))) {
                    canAppr = false;
                }
                if (!assetInput.toBeDeleted()) {
                    if (!this.getString("siteid").equalsIgnoreCase(this.getString("fromsiteid")) && this.getPO() != null && this.getPO().isInternal()) {
                        if (!assetInput.getString("assetnum").equals("")) {
                            if (this.getString("matrectransid").equalsIgnoreCase(assetInput.getString("matrectransid"))) {
                                this.rotatingAssetFromReceiving(assetInput);
                            }
                        }
                    }
                    else if (!this.getString("siteid").equalsIgnoreCase(this.getString("fromsiteid")) && this.isShipReceipt()) {
                        if (!assetInput.getString("assetnum").equals("")) {
                            if (this.getString("matrectransid").equalsIgnoreCase(assetInput.getString("matrectransid"))) {
                                this.rotatingAssetFromReceiving(assetInput);
                            }
                        }
                    }
                    else {
                        if (isLargeApprovalAmt) {
                            inVendorPO = true;
                            if (countOfAssetsAdded == 500) {
                                assetSetRemote.save();
                                countOfAssetsAdded = 0;
                                final String newSql = "1=" + i;
                                assetSetRemote = this.getMboServer().getMboSet("ASSET", this.getUserInfo());
                                assetSetRemote.setWhere(newSql);
                                assetSetRemote.reset();
                                assetSetRemote.setOwner(this);
                            }
                        }
                        final SqlFormat sqf = new SqlFormat(this, "assetnum = :1 and siteid= :2");
                        sqf.setObject(1, "ASSET", "ASSETNUM", assetInput.getString("assetnum"));
                        sqf.setObject(2, "ASSET", "SITEID", this.getString("siteid"));
                        final MboSetRemote assetSet = this.getMboSet("$checkasset", "ASSET", sqf.format());
                        if (!assetSet.isEmpty()) {
                            assetSet.reset();
                            final Object[] params = { assetInput.getString("assetnum") };
                            throw new MXApplicationException("inventory", "assetAlreadyExists", params);
                        }
                        assetSet.reset();
                        if (!assetInput.getString("assetnum").equals("")) {
                            int count = 0;
                            int p = 0;
                            while (true) {
                                final MboRemote assetInputCheck = assetInputSet.getMbo(p);
                                if (assetInputCheck == null) {
                                    break;
                                }
                                if (assetInput.getString("assetnum").equalsIgnoreCase(assetInputCheck.getString("assetnum"))) {
                                    ++count;
                                }
                                ++p;
                            }
                            if (count > 1) {
                                final Object[] params2 = { assetInput.getString("assetnum") };
                                throw new MXApplicationException("inventory", "assetAlreadyExists", params2);
                            }
                        }
                        if (this.getString("matrectransid").equalsIgnoreCase(assetInput.getString("matrectransid"))) {
                            if (assetInput.getString("assetnum").equals("") && ((AssetInputSet)assetInputSet).getDontAllowMEA()) {
                                if (j == 0) {
                                    canAppr = false;
                                    ++j;
                                }
                            }
                            else {
                                ++countOfAssetsAdded;
                                final MboRemote newAsset = assetSetRemote.add();
                                newAsset.setValue("assetnum", assetInput.getString("assetnum"), 11L);
                                final boolean childAssetCreatedViaApplyIAS = ((AssetInputRemote)assetInput).getChildAssetCreatedViaApplyIAS();
                                newAsset.setValue("children", childAssetCreatedViaApplyIAS, 2L);
                                String location;
                                if (this.getBoolean("issue")) {
                                    location = this.getString("location");
                                }
                                else if (this.isHolding() && this.getPOLine() != null) {
                                    location = this.getPOLine().getString("storeloc");
                                }
                                else {
                                    location = this.getString("tostoreloc");
                                }
                                newAsset.setValue("location", location, 11L);
                                newAsset.setValue("itemsetid", assetInput.getString("itemsetid"), 11L);
                                newAsset.setValue("itemnum", assetInput.getString("itemnum"), 3L);
                                if (!isLargeApprovalAmt) {
                                    final ItemSetRemote itemSet = (ItemSetRemote)newAsset.getMboSet("$item", "ITEM", "itemnum=:itemnum and itemsetid=:itemsetid");
                                    if (!itemSet.isEmpty()) {
                                        newAsset.setValue("itemtype", itemSet.getMbo(0).getString("itemtype"), 11L);
                                        itemSet.reset();
                                    }
                                    else {
                                        itemSet.reset();
                                    }
                                    final ItemOrgInfoSetRemote itemOrgInfoSet = (ItemOrgInfoSetRemote)newAsset.getMboSet("$itemorginfo", "ITEMORGINFO", "itemnum=:itemnum and itemsetid=:itemsetid and orgid=:orgid");
                                    if (!itemOrgInfoSet.isEmpty()) {
                                        newAsset.setValue("toolrate", itemOrgInfoSet.getMbo(0).getString("toolrate"), 11L);
                                        newAsset.setValue("TOOLCONTROLACCOUNT", itemOrgInfoSet.getMbo(0).getString("CONTROLACC"), 11L);
                                        itemOrgInfoSet.reset();
                                    }
                                    else {
                                        itemOrgInfoSet.reset();
                                    }
                                }
                                newAsset.setValue("conditioncode", assetInput.getString("conditioncode"), 11L);
                                newAsset.setValue("description", assetInput.getString("description"), 2L);
                                newAsset.setValue("glaccount", assetInput.getString("glaccount"), 11L);
                                newAsset.setValue("rotsuspacct", assetInput.getString("rotsuspacct"), 2L);
                                final double receiptprice = this.getDouble("unitcost");
                                newAsset.setValue("invcost", this.getDouble("unitcost"), 2L);
                                newAsset.setValue("purchaseprice", this.getDouble("unitcost"), 2L);
                                newAsset.setValue("serialnum", assetInput.getString("serialnum"), 2L);
                                newAsset.setValue("rotsuspacct", assetInput.getString("rotsuspacct"), 2L);
                                newAsset.setValue("binnum", this.getString("tobin"), 11L);
                                if (this.getPO() != null) {
                                    newAsset.setValue("vendor", this.getPO().getString("vendor"), 11L);
                                }
                                newAsset.setValue("manufacturer", this.getString("manufacturer"), 11L);
                                if (this.getPO() != null && this.getPO().getBoolean("internal") && !this.getPO().getString("storelocsiteid").equalsIgnoreCase(this.getPOLine().getString("tositeid"))) {
                                    newAsset.setValue("assetid", assetInput.getString("assetid"), 11L);
                                }
                                if (parent != null) {
                                    newAsset.setValue("parent", parent, 11L);
                                }
                                if (!isLargeApprovalAmt || !firstAssetAddedCheckLoc) {
                                    firstAssetAddedCheckLoc = true;
                                    final SqlFormat sqf2 = new SqlFormat(this, "location = :1 and siteid= :2");
                                    sqf2.setObject(1, "LOCATIONS", "LOCATION", this.getString("location"));
                                    sqf2.setObject(2, "LOCATIONS", "SITEID", this.getString("siteid"));
                                    final MboSetRemote locSet = this.getMboSet("$checkLocationsforBeingUsed", "LOCATIONS", sqf2.format());
                                    final LocationRemote loc = (LocationRemote)this.getMboSet("$checkLocationsforBeingUsed", "LOCATIONS", sqf2.format()).getMbo(0);
                                    if (loc != null && !loc.isInventory() && loc.isLocationOccupied(newAsset)) {
                                        loc.getThisMboSet().reset();
                                        final Object[] params3 = { this.getString("location") };
                                        throw new MXApplicationException("asset", "locisoccupied", params3);
                                    }
                                    locSet.reset();
                                }
                                if (((AssetInputSet)assetInputSet).getDontAllowMEA()) {
                                    final MboRemote newAssetTrans = ((AssetRemote)newAsset).createAssetTrans();
                                    newAssetTrans.setValue("matrectransid", assetInput.getString("matrectransid"), 11L);
                                    newAssetTrans.setValue("porevisionnum", this.getString("porevisionnum"), 2L);
                                    newAssetTrans.setValue("datemoved", this.getDate("actualdate"), 11L);
                                    newAssetTrans.setValue("enterby", this.getString("enterby"), 11L);
                                    final MboRemote newMatRec = ((AssetRemote)newAsset).createMatRecTrans();
                                    if (newMatRec != null) {
                                        final String itemNum = assetInput.getString("itemnum");
                                        final double invcost = newAsset.getDouble("invcost");
                                        final MatRecTransSet matRecSet = (MatRecTransSet)newMatRec.getThisMboSet();
                                        if (!firstAssetAdded || !itemNum.equals(lastItemNum)) {
                                            newMatRec.setValue("currencyunitcost", invcost, 2L);
                                            newMatRec.setValue("currencylinecost", 0.0, 11L);
                                            newMatRec.setValue("linecost", 0.0, 11L);
                                            cust.component.Logger.Log("linecost2#3");
                                            newMatRec.setValue("linecost2", 0.0, 11L);
                                            lastLoadedCost = newMatRec.getDouble("loadedcost");
                                            lastItemNum = assetInput.getString("itemnum");
                                            lastIssueType = newMatRec.getString("issuetype");
                                            lastFinancialPeriod = newMatRec.getString("financialperiod");
                                            firstAssetAdded = true;
                                            matRecSet.setToExecuteCompleteAdd(false);
                                        }
                                        else {
                                            newMatRec.setValue("loadedcost", lastLoadedCost, 11L);
                                            newMatRec.setValue("currencyunitcost", invcost, 11L);
                                            newMatRec.setValue("currencylinecost", 0.0, 11L);
                                            newMatRec.setValue("linecost", 0.0, 11L);
                                            cust.component.Logger.Log("linecost2#4");
                                            newMatRec.setValue("linecost2", 0.0, 11L);
                                            newMatRec.setValue("financialperiod", lastFinancialPeriod, 11L);
                                            newMatRec.setValue("issuetype", lastIssueType, 11L);
                                            matRecSet.setToExecuteCompleteAdd(((MatRecTrans)newMatRec).needToExecuteAppValidate = false);
                                        }
                                    }
                                }
                                if (((AssetInputRemote)assetInput).getIASApplied()) {
                                    final MboRemote topItemStruct = assetInput.getMboSet("TOPITEMSTRUCT").getMbo(0);
                                    if (topItemStruct != null) {
                                        ((AssetRemote)newAsset).applyIASGenSpareParts(topItemStruct.getMboSet("CHILDREN_NONROTATING"));
                                    }
                                    assetInput.getMboSet("TOPITEMSTRUCT_1").reset();
                                }
                                if (!assetInput.getString("contractnum").equals("")) {
                                    sql = new SqlFormat(this, "contractnum = :1 and revisionnum = :2 and orgid = :3");
                                    sql.setObject(1, "CONTRACT", "contractnum", assetInput.getString("contractnum"));
                                    sql.setObject(2, "CONTRACT", "revisionnum", assetInput.getString("revisionnum"));
                                    sql.setObject(3, "CONTRACT", "orgid", assetInput.getString("orgid"));
                                    final MboRemote contractRemote = this.getMboSet("$contract", "CONTRACT", sql.format()).getMbo(0);
                                    if (contractRemote != null) {
                                        final String contractType = ((Contract)contractRemote).getInternalContractType();
                                        if (contractType.equals("LEASE") || contractType.equals("RENTAL") || contractType.equals("PURCHASE") || contractType.equals("PRICE") || contractType.equals("BLANKET")) {
                                            final MboSetRemote contractAssetSet = ((Mbo)assetInput).getMboSet("$contractasset", "CONTRACTASSET", sql.format());
                                            final MboRemote newContractAsset = contractAssetSet.add();
                                            newContractAsset.setValue("revisionnum", assetInput.getString("revisionnum"), 2L);
                                            newContractAsset.setValue("contractnum", assetInput.getString("contractnum"), 3L);
                                            newContractAsset.setValue("contassetlinenum", contractAssetSet.max("contassetlinenum") + i, 11L);
                                            newContractAsset.setValue("assetid", newAsset.getString("assetid"), 2L);
                                            newContractAsset.setValue("location", location, 3L);
                                            newContractAsset.setValue("totalcost", this.getDouble("unitcost"), 3L);
                                            MboRemote leaseOrPurchView = null;
                                            if (contractType.equals("BLANKET") || contractType.equals("PRICE") || contractType.equals("PURCHASE")) {
                                                leaseOrPurchView = contractRemote.getMboSet("PURCHVIEW").getMbo(0);
                                            }
                                            else if (contractType.equals("LEASE") || contractType.equals("RENTAL")) {
                                                newContractAsset.setValue("startdate", MXServer.getMXServer().getDate(), 11L);
                                                leaseOrPurchView = contractRemote.getMboSet("LEASEVIEW").getMbo(0);
                                                final int duration = leaseOrPurchView.getInt("term");
                                                if (duration != 0) {
                                                    final Calendar tempCal = new GregorianCalendar();
                                                    tempCal.setTime(newContractAsset.getDate("startdate"));
                                                    tempCal.add(2, duration);
                                                    tempCal.add(5, -1);
                                                    newContractAsset.setValue("enddate", tempCal.getTime(), 2L);
                                                }
                                            }
                                            if (leaseOrPurchView != null) {
                                                newAsset.setValue("mainthierchy", leaseOrPurchView.getBoolean("mainthierchy"), 2L);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            ++i;
        }
    }
    
    protected void setLottedEditibilityFlags(final boolean flag) throws MXException, RemoteException {
        final String[] matRecFields = { "actualcost", "belongsto", "costinfo", "currencylinecost", "currencyunitcost", "description", "assetnum", "externalrefid", "fincntrlid", "frombin", "fromlot", "fromstoreloc", "glcreditacct", "gldebitacct", "invoicenum", "issueto", "issuetype", "it1", "it2", "it3", "it4", "it5", "it6", "it7", "it8", "it9", "it10", "itemnum", "itin1", "itin2", "itin3", "itin4", "itin5", "itin6", "itin7", "ldkey", "location", "matrectransid", "mrlinenum", "mrnum", "ownersysid", "polinenum", "ponum", "requestedby", "sendersysid", "siteid", "sourcesysid", "status", "taskid", "tostoreloc", "wonum" };
        this.setFieldFlag(matRecFields, 7L, flag);
    }
    
    protected void setEditibilityFlags(final boolean flag) throws MXException, RemoteException {
        final String[] matRecFields = { "actualcost", "actualdate", "belongsto", "costinfo", "currencylinecost", "currencyunitcost", "description", "enterby", "assetnum", "externalrefid", "fincntrlid", "frombin", "fromlot", "fromstoreloc", "glcreditacct", "gldebitacct", "invoicenum", "issueto", "issuetype", "it1", "it2", "it3", "it4", "it5", "it6", "it7", "it8", "it9", "it10", "itemnum", "itin1", "itin2", "itin3", "itin4", "itin5", "itin6", "itin7", "ldkey", "loaction", "manufacturer", "matrectransid", "modelnum", "mrlinenum", "mrnum", "ownersysid", "packingslipnum", "polinenum", "ponum", "receivedunit", "rejectcode", "rejectqty", "remark", "requestedby", "sendersysid", "siteid", "sourcesysid", "status", "taskid", "tobin", "tostoreloc", "receiptquantity", "wonum" };
        this.setFieldFlag(matRecFields, 7L, flag);
    }
    
    public void setInspectionRequiredEditibilityFlags(final boolean flag) throws MXException, RemoteException {
        final String[] matRecFields = { "actualcost", "belongsto", "costinfo", "currencylinecost", "currencyunitcost", "description", "externalrefid", "fincntrlid", "frombin", "fromlot", "fromstoreloc", "glcreditacct", "gldebitacct", "inspectedqty", "invoicenum", "issueto", "issuetype", "it1", "it10", "it2", "it3", "it4", "it5", "it6", "it7", "it8", "it9", "itemnum", "itin1", "itin2", "itin3", "itin4", "itin5", "itin6", "itin7", "ldkey", "location", "matrectransid", "mfglotnum", "mrlinenum", "mrnum", "ownersysid", "polinenum", "ponum", "receivedunit", "requestedby", "rejectqty", "shelflife", "sendersysid", "siteid", "sourcesysid", "status", "taskid", "tolot", "tostoreloc", "useby", "wonum" };
        this.setFieldFlag(matRecFields, 7L, flag);
    }
    
    public void canApprove() throws MXException, RemoteException {
        if (this.getReceiptStatus().equals("COMP")) {
            throw new MXApplicationException("inventory", "cannotApprove");
        }
    }
    
    public void onlyPayOnReceipt(final PORemote poMbo, final POLineRemote poLineMbo) throws MXException, RemoteException {
        if (poLineMbo != null && poLineMbo.getDouble("scheduleid") != 0.0) {
            return;
        }
        final String issueType = this.getTranslator().toInternalString("ISSUETYP", this.getString("issuetype"));
        if (issueType.equalsIgnoreCase("POCOST")) {
            return;
        }
        final MboRemote owner = this.getOwner();
        if (!poLineMbo.isInspectionRequired() || this.getReceiptStatus().equals("COMP")) {
            if (!this.isNull("ponum") && (owner == null || (!(owner instanceof InvoiceRemote) && !(owner instanceof InvoiceLineRemote) && !(owner instanceof InvoiceCostRemote)))) {
                this.payOnReceipt();
            }
            if (this.isLastReceipt()) {
                this.approveInvoices();
            }
            if (this.getTranslator().toInternalString("RECEIPTS", poMbo.getString("receipts")).equals("COMPLETE")) {
                final String status = this.getTranslator().toExternalDefaultValue("POSTATUS", "CLOSE", this);
                try {
                    if (!poMbo.getInternalStatus().equals("CLOSE")) {
                        final boolean autoClosePO = this.getMboServer().getMaxVar().getBoolean("AUTOCLOSEPO", this.getOrgSiteForMaxvar("AUTOCLOSEPO"));
                        if (autoClosePO) {
                            poMbo.changeStatus(status, MXServer.getMXServer().getDate(), "");
                        }
                    }
                }
                catch (Throwable t) {}
            }
        }
    }
    
    private void adjustPhysicalCount() throws MXException, RemoteException {
        final String issueType = this.getTranslator().toInternalString("ISSUETYP", this.getString("issuetype"));
        if (!this.isNull("newphyscnt") && issueType.equalsIgnoreCase("TRANSFER")) {
            final Inventory inv = (Inventory)this.validateInventory(this.getString("fromstoreloc"), this.getString("fromsiteid"));
            inv.adjustPhysicalCount(this.getString("frombin"), this.getString("fromlot"), this.getDouble("newphyscnt"), this.getDate("newphyscntdate"), null, this.getString("conditionCode"));
        }
    }
    
    public void setToInvBalUpdated() throws MXException, RemoteException {
        this.toInvBalUpdated = true;
    }
    
    boolean getToInvBalUpdated() throws MXException, RemoteException {
        return this.toInvBalUpdated;
    }
    
    public void setFromInvBalUpdated() throws MXException, RemoteException {
        this.fromInvBalUpdated = true;
    }
    
    boolean getFromInvBalUpdated() throws MXException, RemoteException {
        return this.fromInvBalUpdated;
    }
    
    void setCourierLaborTransientMatRec(final MboRemote matRec) throws MXException, RemoteException {
        this.courierLaborMatRec = matRec;
    }
    
    public MboRemote getCourierLaborTransientMatRec() throws MXException, RemoteException {
        return this.courierLaborMatRec;
    }
    
    @Override
    public MboRemote getSharedInventory(final String storeLoc, final String siteid) throws MXException, RemoteException {
        final MboRemote owner = this.getOwner();
        if (owner instanceof InventoryRemote && owner.getString("location").equals(storeLoc) && owner.getString("siteid").equals(siteid)) {
            return owner;
        }
        final SqlFormat sqf = new SqlFormat(this, "itemnum = :itemnum and location = :1 and siteid = :2 and itemsetid=:itemsetid");
        sqf.setObject(1, "INVENTORY", "location", storeLoc);
        sqf.setObject(2, "INVENTORY", "siteid", siteid);
        final MboSetRemote invSet = ((MboSet)this.getThisMboSet()).getSharedMboSet("INVENTORY", sqf.format());
        if (invSet != null) {
            invSet.setOwner(this);
        }
        if (invSet == null || invSet.isEmpty()) {
            return null;
        }
        return invSet.getMbo(0);
    }
    
    MboRemote validateInventory(final String location, final String siteid) throws MXException, RemoteException {
        final SqlFormat sqf = new SqlFormat(this, "itemnum = :itemnum and itemsetid = :itemsetid and location = :1 and siteid = :2");
        sqf.setObject(1, "INVENTORY", "location", location);
        sqf.setObject(2, "INVENTORY", "siteid", siteid);
        final MboSetRemote invSet = this.getMboSet("$$$getInvSet_loc_site", "INVENTORY", sqf.format());
        if (invSet.isEmpty()) {
            throw new MXApplicationException("inventory", "invbalNotInInventory");
        }
        invSet.reset();
        return invSet.getMbo(0);
    }
    
    double getDefaultIssueCostForTransfer() throws MXException, RemoteException {
        Inventory fromInventory = null;
        double cost = 0.0;
        try {
            fromInventory = (Inventory)this.validateInventory(this.getString("fromstoreloc"), this.getString("fromsiteid"));
        }
        catch (Exception ex) {}
        if (fromInventory != null) {
            cost = fromInventory.getDefaultIssueCost(this.getString("fromconditioncode"));
        }
        return cost;
    }
    
    MboRemote getTopLevelOwner(MboRemote owner) {
        MboRemote topOwner = owner;
        while (owner != null) {
            try {
                owner = owner.getOwner();
            }
            catch (Exception ex) {}
            if (owner != null) {
                topOwner = owner;
            }
        }
        return topOwner;
    }
    
    public double[] calculateUnInvoicedQtyCost() throws MXException, RemoteException {
        final double[] calculatedValues = new double[2];
        final MboSetRemote invoiceMatches = this.getMboSet("$match", "invoicematch", "matrectransid=:matrectransid and reversed=:no");
        MboRemote match = null;
        double invoicedQty = 0.0;
        double invoiceMatchConversion = 1.0;
        for (int i = 0; (match = invoiceMatches.getMbo(i)) != null; ++i) {
            if (!match.toBeDeleted()) {
                if (!match.isNull("conversion") && match.getDouble("conversion") != 0.0) {
                    invoiceMatchConversion = match.getDouble("conversion");
                }
                invoicedQty = MXMath.add(invoicedQty, MXMath.multiply(match.getDouble("quantity"), invoiceMatchConversion));
            }
        }
        final MboRemote owner = this.getOwner();
        if (owner instanceof InvoiceRemote) {
            final MboSetRemote invoiceLines = owner.getMboSet("INVOICELINE");
            MboRemote invoiceLine = null;
            double invoiceLineConversion = 1.0;
            for (int j = 0; (invoiceLine = invoiceLines.getMbo(j)) != null; ++j) {
                if (invoiceLine.toBeAdded() && invoiceLine.getString("matrectransid").equalsIgnoreCase(this.getString("matrectransid"))) {
                    if (!invoiceLine.isNull("conversion") && invoiceLine.getDouble("conversion") != 0.0) {
                        invoiceLineConversion = invoiceLine.getDouble("conversion");
                    }
                    invoicedQty = MXMath.add(invoicedQty, MXMath.multiply(invoiceLine.getDouble("invoiceqty"), invoiceLineConversion));
                }
                else if (invoiceLine.toBeDeleted() && invoiceLine.isNull("matrectransid")) {
                    final SqlFormat sqf = new SqlFormat(this, "invoicenum=:1 and invoicelinenum=:2 and matrectransid=:matrectransid");
                    sqf.setObject(1, "invoicematch", "invoicenum", invoiceLine.getString("invoicenum"));
                    sqf.setObject(2, "invoicematch", "invoicelinenum", invoiceLine.getString("invoicelinenum"));
                    final MboSetRemote matSet = this.getMboSet("$invoicematch", "invoicematch", sqf.format());
                    invoiceLineConversion = 1.0;
                    if (!invoiceLine.isNull("conversion") && invoiceLine.getDouble("conversion") != 0.0) {
                        invoiceLineConversion = invoiceLine.getDouble("conversion");
                    }
                    invoicedQty = MXMath.subtract(invoicedQty, MXMath.multiply(matSet.sum("quantity"), invoiceLineConversion));
                }
                else if (invoiceLine.isNull("matrectransid")) {
                    final MboRemote invMatch = invoiceLine.getMboSet("INVOICEMATCH").getMbo(0);
                    if (invMatch == null && this.getString("ponum").equalsIgnoreCase(invoiceLine.getString("ponum")) && this.getInt("polinenum") == invoiceLine.getInt("polinenum") && this.getString("positeid").equalsIgnoreCase(invoiceLine.getString("positeid"))) {
                        if (!invoiceLine.isNull("conversion") && invoiceLine.getDouble("conversion") != 0.0) {
                            invoiceLineConversion = invoiceLine.getDouble("conversion");
                        }
                        invoicedQty = MXMath.add(invoicedQty, MXMath.multiply(invoiceLine.getDouble("invoiceqty"), invoiceLineConversion));
                    }
                }
            }
        }
        calculatedValues[0] = MXMath.subtract(this.getDouble("quantity"), invoicedQty);
        if (!this.isNull("conversion") && this.getDouble("conversion") != 0.0) {
            final double conversion = this.getDouble("conversion");
            calculatedValues[0] = RoundToScale.round(this.getMboValue("quantity"), MXMath.divide(calculatedValues[0], conversion));
        }
        if (this.getDouble("quantity") != 0.0) {
            calculatedValues[1] = MXMath.subtract(this.getDouble("currencylinecost"), MXMath.multiply(invoicedQty, MXMath.divide(this.getDouble("currencylinecost"), this.getDouble("quantity"))));
        }
        else {
            calculatedValues[1] = MXMath.subtract(this.getDouble("currencylinecost"), MXMath.multiply(invoicedQty, this.getDouble("currencyunitcost")));
        }
        return calculatedValues;
    }
    
    private boolean getInvoiceMgtMaxVar() throws MXException, RemoteException {
        return this.getMboServer().getMaxVar().getBoolean("INVOICEMGT", this.getOrgSiteForMaxvar("INVOICEMGT"));
    }
    
    protected void setConversionFactor() throws MXException, RemoteException {
        String fromMeasureUnit = "";
        String toMeasureUnit = "";
        if (this.isNull("itemnum") || (this.isNull("fromstoreloc") && this.isNull("ponum")) || this.isNull("tostoreloc")) {
            return;
        }
        if (!this.isNull("polinenum") && !this.isNull("conversion")) {
            if (!this.getMboValue("receivedunit").isModified()) {
                return;
            }
            if (this.getString("receivedunit").equalsIgnoreCase(this.getMboValue("receivedunit").getPreviousValue().asString())) {
                return;
            }
        }
        final MboSetRemote itemSet = this.getMboSet("ITEM");
        if (!itemSet.isEmpty()) {
            final ItemRemote item = (ItemRemote)itemSet.getMbo(0);
            if (item.isRotating() || item.isKit()) {
                this.setValue("conversion", 1, 2L);
                this.setFieldFlag("conversion", 7L, true);
                return;
            }
            this.setFieldFlag("conversion", 7L, false);
        }
        if ((this.isReceipt() || this.isTransferWithPO() || this.isMisclReceipt()) && !this.isNull("receivedunit")) {
            fromMeasureUnit = this.getString("receivedunit");
        }
        if (fromMeasureUnit == "" && !this.isNull("fromstoreloc")) {
            final MboRemote mbor = this.getMboSet("FROMINVENTORY").getMbo(0);
            if (mbor != null) {
                fromMeasureUnit = mbor.getString("ISSUEUNIT");
                if (this.isTransfer()) {
                    this.setValue("receivedunit", fromMeasureUnit, 11L);
                }
            }
        }
        final MboRemote toInventory = this.getMboSet("INVENTORY").getMbo(0);
        if (toInventory == null) {
            final MboRemote owner = this.getOwner();
            if (this.isNull("conversion") && owner != null && owner instanceof LocationRemote && !owner.getMboSet("MATRECTRANSIN").isEmpty()) {
                this.setValue("conversion", 1, 11L);
            }
            return;
        }
        toMeasureUnit = toInventory.getString("ISSUEUNIT");
        if (fromMeasureUnit == "" || toMeasureUnit == "") {
            return;
        }
        final InventoryServiceRemote invService = (InventoryServiceRemote)MXServer.getMXServer().lookup("INVENTORY");
        double conversion = 0.0;
        String conversionDisplay = "";
        try {
            conversion = invService.getConversionFactor(this.getUserInfo(), fromMeasureUnit, toMeasureUnit, this.getString("itemsetid"), this.getString("itemnum"));
            if (conversion > 0.0) {
                conversionDisplay = Double.toString(conversion);
                this.setValue("conversion", conversion, 2L);
            }
        }
        catch (MXApplicationException mxe) {
            if (conversion > 0.0 && mxe.getErrorKey().equalsIgnoreCase("conversionIsZero")) {
                final Object[] params = { conversionDisplay };
                throw new MXApplicationException("inventory", "conversionPrecisionLow", params);
            }
            if (!mxe.getErrorKey().equalsIgnoreCase("conversionDoesNotExist")) {
                throw mxe;
            }
            this.conversionException = mxe;
            this.setValueNull("conversion", 2L);
        }
    }
    
    protected boolean allKitComponentsAreInTransferToStore() throws MXException, RemoteException {
        if ((!(this.getOwner() instanceof LocationRemote) && !this.getOwner().isBasedOn("InvUse")) || !this.isTransfer() || this.isNull("itemnum") || this.isNull("tostoreloc") || !this.isStore() || (this.isNull("siteid") && this.isNull("newsite")) || this.isHolding()) {
            return true;
        }
        final MboSetRemote itemSet = this.getMboSet("ITEM");
        ItemRemote item = null;
        if (!itemSet.isEmpty()) {
            item = (ItemRemote)itemSet.getMbo(0);
        }
        final String storeroom = this.getString("ToStoreLoc");
        if (item != null && item.isKit()) {
            if (this.locService == null) {
                this.locService = (LocationServiceRemote)MXServer.getMXServer().lookup("LOCATION");
            }
            if (this.kitComponentsToAddToStore == null) {
                final Hashtable defaultBins = new Hashtable();
                defaultBins.put(this.getString("itemnum"), this.getString("tobin"));
                this.kitComponentsToAddToStore = (ItemSetRemote)this.locService.getKitComponentsNotYetInStore(this.getUserInfo(), item, storeroom, defaultBins);
            }
            if (((MatRecTransSet)this.getThisMboSet()).addKitComponentsToStores() && this.kitComponentsToAddToStore != null) {
                this.locService.addItemsToStoreroom(this.getUserInfo(), this.kitComponentsToAddToStore, storeroom, true, this);
                return true;
            }
            if (this.kitComponentsToAddToStore != null && !this.kitComponentsToAddToStore.isEmpty()) {
                return this.toAddOrNotToAddKitComponents(this.kitComponentsToAddToStore, this.locService);
            }
        }
        return true;
    }
    
    private boolean toAddOrNotToAddKitComponents(final ItemSetRemote kitComponentsToAddToStore, final LocationServiceRemote locService) throws MXException, RemoteException {
        final UserInfo userInfo = this.getUserInfo();
        if (!userInfo.isInteractive()) {
            return false;
        }
        final String storeroom = this.getString("ToStoreLoc");
        final int userInput = MXApplicationYesNoCancelException.getUserInput("InvIssueAddKitComponentsInteractId", MXServer.getMXServer(), userInfo);
        switch (userInput) {
            case -1: {
                throw new MXApplicationYesNoCancelException("InvIssueAddKitComponentsInteractId", "item", "componentsNotInStore");
            }
            case 2: {
                ((MatRecTransSet)this.getThisMboSet()).setAddKitComponentsToStores();
                locService.addItemsToStoreroom(this.getUserInfo(), kitComponentsToAddToStore, storeroom, true, this);
                return true;
            }
            case 4: {
                return false;
            }
            default: {
                return false;
            }
        }
    }
    
    protected void updateKitComponentNewInventoryBinnum(final String binnum) throws MXException, RemoteException {
        if (!(this.getOwner() instanceof LocationRemote) || !this.isTransfer() || binnum == null || binnum.equals("")) {
            return;
        }
        final MboSetRemote itemSet = this.getMboSet("ITEM");
        if (!itemSet.isEmpty() && !((ItemRemote)itemSet.getMbo(0)).isKit()) {
            return;
        }
        int i = 0;
        final MboSetRemote newInvSet = this.getMboSet("NEW_INVENTORY");
        MboRemote newInv = null;
        while ((newInv = newInvSet.getMbo(i)) != null) {
            newInv.setValue("binnum", binnum, 11L);
            ++i;
        }
    }
    
    @Override
    public void createReturn(final MatRecTransRemote matrec) throws MXException, RemoteException {
        this.copyMatRecTransToMatRecTrans(matrec, "RETURN");
    }
    
    protected void copyMatRecTransToMatRecTrans(final MatRecTransRemote matrec, final String issueType) throws MXException, RemoteException {
        cust.component.Logger.Log("MatRecTrans.copyMatRecTransToMatRecTrans");
        final MboRemote owner = this.getOwner();
        MboRemote shipmentTransferMbo = null;
        boolean isShipmentType = false;
        if (owner instanceof ShipmentRemote || (owner.getOwner() != null && owner.getOwner() instanceof ShipmentRemote)) {
            isShipmentType = true;
        }
        if (isShipmentType) {
            final SqlFormat sqf = new SqlFormat(this, "invuselinesplitid=:invuselinesplitid and issuetype in (select value from synonymdomain where domainid='ISSUETYP' and maxvalue='SHIPTRANSFER')");
            final MboSetRemote matSet = this.getMboSet("$shipment_line_for_transfer", "matrectrans", sqf.format());
            shipmentTransferMbo = matSet.getMbo(0);
        }
        final POLineRemote poLineMbo = this.getPOLine();
        final PORemote poRemote = this.getPO();
        matrec.setValue("fromsiteid", this.getString("siteid"), 11L);
        matrec.setPropagateKeyFlag(false);
        matrec.setValue("siteid", this.getString("siteid"), 11L);
        matrec.setPropagateKeyFlag(true);
        matrec.setValue("newsite", this.getString("siteid"), 11L);
        matrec.setPropagateKeyFlag(false);
        if (issueType.equals("SHIPRETURN") && matrec.getMXTransaction().getBoolean("integration")) {
            matrec.setValue("siteid", this.getString("fromsiteid"), 11L);
        }
        matrec.setPropagateKeyFlag(true);
        if (issueType.equals("TRANSFER")) {
            if (isShipmentType) {
                matrec.setValue("tostoreloc", shipmentTransferMbo.getString("tostoreloc"), 11L);
            }
            else {
                matrec.setValue("tostoreloc", poLineMbo.getString("storeloc"), 11L);
            }
        }
        if (isShipmentType && (issueType.equals("TRANSFER") || issueType.equals("SHIPRETURN"))) {
            matrec.setValue("shipmentnum", shipmentTransferMbo.getString("shipmentnum"), 11L);
            matrec.setValue("shipmentlinenum", shipmentTransferMbo.getString("shipmentlinenum"), 11L);
            matrec.setValue("positeid", this.getString("positeid"), 11L);
            matrec.setValue("INVUSELINESPLITID", shipmentTransferMbo.getString("INVUSELINESPLITID"), 11L);
            matrec.setValue("INVUSELINENUM", shipmentTransferMbo.getString("INVUSELINENUM"), 11L);
            matrec.setValue("INVUSEID", shipmentTransferMbo.getString("INVUSEID"), 11L);
            matrec.setValue("INVUSELINEID", shipmentTransferMbo.getString("INVUSELINEID"), 11L);
            if (this.getMXTransaction().getBoolean("integration")) {
                final MboRemote invUseLineSplitRemote = shipmentTransferMbo.getMboSet("INVUSELINESPLIT").getMbo(0);
                if (invUseLineSplitRemote != null && issueType.equals("SHIPRETURN")) {
                    matrec.setValue("frombin", invUseLineSplitRemote.getString("frombin"), 2L);
                }
            }
        }
        matrec.setValue("ownersysid", this.getString("ownersysid"), 2L);
        matrec.setValue("fromstoreloc", this.getString("tostoreloc"), 11L);
        matrec.setValue("issuetype", this.getTranslator().toExternalDefaultValue("ISSUETYP", issueType, this), 11L);
        matrec.setValue("itemsetid", this.getString("itemsetid"), 3L);
        matrec.setValue("itemnum", this.getString("itemnum"), 3L);
        matrec.setValue("tolot", this.getString("tolot"), 2L);
        matrec.setValue("description", this.getString("description"));
        matrec.setValue("receivedunit", this.getString("receivedunit"), 2L);
        matrec.setValue("linetype", this.getString("linetype"), 11L);
        matrec.setValue("manufacturer", this.getString("manufacturer"), 11L);
        matrec.setValue("modelnum", this.getString("modelnum"), 11L);
        if (!this.isNull("remark")) {
            matrec.setValue("remark", this.getString("remark"), 11L);
        }
        if (issueType.equalsIgnoreCase("TRANSFER")) {
            matrec.setFieldFlag("rotassetnum", 7L, true);
            matrec.setFieldFlag("rotassetnum", 128L, false);
        }
        double conversionFactor = this.getDouble("conversion");
        if (conversionFactor == 0.0) {
            conversionFactor = 1.0;
        }
        if (issueType.equalsIgnoreCase("RETURN") || issueType.equalsIgnoreCase("SHIPRETURN")) {
            if (this.isApprovingReceipt()) {
                matrec.setValue("tobin", this.getString("tobin"), 11L);
            }
            else {
                matrec.setValue("tobin", this.getString("frombin"), 11L);
            }
            if (issueType.equalsIgnoreCase("SHIPRETURN") && shipmentTransferMbo != null) {
                matrec.setValue("tostoreloc", shipmentTransferMbo.getString("fromstoreloc"), 11L);
            }
            else {
                matrec.setValue("tostoreloc", this.getString("tostoreloc"), 11L);
            }
            if (issueType.equalsIgnoreCase("SHIPRETURN") && !this.isNull("itemnum") && this.getBoolean("ITEM.rotating")) {
                matrec.setValue("quantity", -1.0 * conversionFactor, 11L);
                matrec.setValue("receiptquantity", -1, 11L);
            }
            else {
                matrec.setValue("quantity", this.getDouble("rejectqtydisplay") * conversionFactor * -1.0, 11L);
                matrec.setValue("receiptquantity", this.getDouble("rejectqtydisplay") * -1.0, 11L);
            }
            matrec.setValue("status", "!COMP!", 11L);
            matrec.setValue("currencylinecost", matrec.getDouble("quantity") * this.getDouble("currencyunitcost"), 11L);
        }
        else {
            matrec.setValue("tobin", this.getString("tobin"), 11L);
            if (!isShipmentType) {
                matrec.setValue("tostoreloc", poLineMbo.getString("storeloc"), 11L);
            }
            matrec.setValue("wonum", this.getString("wonum"), 11L);
            matrec.setValue("refwo", this.getString("wonum"), 11L);
            matrec.setValue("location", this.getString("location"), 11L);
            matrec.setValue("packingslipnum", this.getString("packingslipnum"), 11L);
            if (!this.isNull("itemnum") && this.getBoolean("ITEM.rotating")) {
                if (this.isInspectionRequired() && !isShipmentType) {
                    matrec.setValue("quantity", (this.getDouble("inspectedqty") - this.getDouble("rejectqty")) * conversionFactor, 11L);
                    matrec.setValue("receiptquantity", this.getDouble("inspectedqty") - this.getDouble("rejectqty"), 11L);
                }
                else if (!isShipmentType) {
                    matrec.setValue("quantity", this.getDouble("acceptedqty") * conversionFactor, 11L);
                    matrec.setValue("receiptquantity", this.getDouble("acceptedqty"), 11L);
                }
                else {
                    matrec.setValue("quantity", 1.0 * conversionFactor, 11L);
                    matrec.setValue("receiptquantity", 1, 11L);
                }
                if (issueType.equals("SHIPRECEIPT") && !this.getString("siteid").equalsIgnoreCase(this.getString("fromsiteid"))) {
                    matrec.setValue("rotassetnum", this.getString("rotassetnum"), 11L);
                }
            }
            else {
                matrec.setValue("quantity", this.getDouble("acceptedqty") * conversionFactor, 11L);
                matrec.setValue("receiptquantity", this.getDouble("acceptedqty"), 11L);
            }
            if (!this.isNull("itemnum") && this.getBoolean("ITEM.rotating") && this.getReceiptStatus().equals("WINSP")) {
                matrec.setValue("status", "!WASSET!", 11L);
            }
            else {
                matrec.setValue("status", "!COMP!", 11L);
            }
        }
        matrec.setValue("receiptref", this.getString("matrectransid"), 11L);
        matrec.setValue("externalrefid", this.getString("externalrefid"), 11L);
        matrec.setValue("fromconditioncode", this.getString("conditioncode"), 11L);
        matrec.setValue("ponum", this.getString("ponum"), 11L);
        matrec.setValue("polinenum", this.getString("polinenum"), 11L);
        matrec.setValue("issue", this.getBoolean("issue"), 11L);
        matrec.setValue("fromlot", this.getString("fromlot"), 11L);
        matrec.setValue("tolot", this.getString("tolot"), 11L);
        matrec.setValue("conditioncode", this.getString("conditioncode"), 11L);
        matrec.setValue("conversion", this.getDouble("conversion"), 11L);
        matrec.setValue("currencyunitcost", this.getDouble("currencyunitcost"), 11L);
        matrec.setValue("currencylinecost", matrec.getDouble("currencyunitcost") * matrec.getDouble("quantity"), 11L);
        matrec.setValue("unitcost", this.getDouble("unitcost"), 11L);
        matrec.setValue("displayunitcost", this.getDouble("unitcost"), 11L);
        matrec.setValue("actualcost", this.getDouble("unitcost"), 11L);
        if (!this.isNull("exchangerate")) {
            matrec.setValue("linecost", this.getDouble("currencyunitcost") * matrec.getDouble("quantity") * this.getDouble("exchangerate"), 2L);
        }
        else {
            matrec.setValue("linecost", this.getDouble("unitcost") * matrec.getDouble("quantity"), 2L);
        }
        if (this.getBoolean("prorated") && !this.isNull("proratecost")) {
            matrec.setValue("proratecost", this.getDouble("proratecost"), 11L);
            matrec.setValue("loadedcost", this.getDouble("loadedcost"), 11L);
        }
        matrec.setValue("COMMODITY", this.getString("COMMODITY"), 11L);
        matrec.setValue("COMMODITYGROUP", this.getString("COMMODITYGROUP"), 11L);
        TaxUtility.getTaxUtility().setTaxattrValue(matrec, "TAXCODE", this, 2L);
        matrec.setValue("mrnum", this.getString("mrnum"), 11L);
        matrec.setValue("mrlinenum", this.getString("mrlinenum"), 11L);
        try {
            matrec.setValue("actualdate", this.getDate("actualdate"), 2L);
        }
        catch (MXException mxe) {
            final MatRecTransSet matSet2 = (MatRecTransSet)matrec.getThisMboSet();
            final int numberOfExceptions = matSet2.getNumberOfActualDateExceptions();
            if (mxe.getErrorGroup().equalsIgnoreCase("financial") && mxe.getErrorKey().equalsIgnoreCase("closedfinperiod") && numberOfExceptions == 0) {
                matrec.delete();
            }
            if (numberOfExceptions == 0) {
                matSet2.incrNumberOfActualDateExceptions(1);
                throw mxe;
            }
        }
        matrec.setValue("actualdate", this.getDate("actualdate"), 11L);
        final MboRemote mbr = this.getHoldingLocationForSite(this.getString("siteid"));
        if (matrec.getBoolean("issue") && !matrec.isReject()) {
            matrec.setValue("gldebitacct", poLineMbo.getString("gldebitacct"), 11L);
        }
        else if (issueType.equalsIgnoreCase("TRANSFER")) {
            final LocationRemote toLoc = (LocationRemote)matrec.getMboSet("LOCATIONS").getMbo(0);
            if (this.getTranslator().toInternalString("LINETYPE", this.getString("linetype")).equals("TOOL")) {
                matrec.setValue("gldebitacct", toLoc.getString("toolcontrolacc"), 11L);
            }
            else if (!this.isNull("itemnum")) {
                final ItemRemote itemr = (ItemRemote)matrec.getMboSet("ITEM").getMbo(0);
                if (itemr != null && itemr.isCapitalized()) {
                    final InventoryRemote invr = (InventoryRemote)matrec.getMboSet("INVENTORY").getMbo(0);
                    if (invr != null) {
                        matrec.setValue("gldebitacct", invr.getString("controlacc"), 11L);
                    }
                    else {
                        matrec.setValue("gldebitacct", toLoc.getString("controlacc"), 11L);
                    }
                }
                else {
                    if (itemr != null) {
                        final InventoryRemote invr = (InventoryRemote)matrec.getMboSet("INVENTORY").getMbo(0);
                        if (invr != null) {
                            MboRemote invCost = null;
                            invCost = ((Inventory)invr).getInvCostRecord(this.getString("conditioncode"));
                            if (invCost != null && !invCost.isNull("controlacc")) {
                                matrec.setValue("gldebitacct", invCost.getString("controlacc"), 11L);
                            }
                            else {
                                matrec.setValue("gldebitacct", invr.getString("controlacc"), 11L);
                            }
                        }
                    }
                    if (matrec.isNull("gldebitacct")) {
                        matrec.setValue("gldebitacct", toLoc.getString("controlacc"), 11L);
                    }
                }
            }
            else {
                matrec.setValue("gldebitacct", toLoc.getString("controlacc"), 11L);
            }
        }
        else if (issueType.equalsIgnoreCase("RETURN") || issueType.equalsIgnoreCase("SHIPRETURN")) {
            if (mbr != null) {
                matrec.setValue("gldebitacct", mbr.getString("glaccount"), 11L);
            }
            else {
                matrec.setValue("gldebitacct", this.getString("glcreditacct"), 11L);
            }
        }
        if (issueType.equalsIgnoreCase("RETURN") || issueType.equalsIgnoreCase("SHIPRETURN")) {
            matrec.setValue("glcreditacct", this.getString("glcreditacct"), 11L);
        }
        else if (mbr != null) {
            matrec.setValue("glcreditacct", mbr.getString("glaccount"), 11L);
        }
    }
    
    boolean inSameOrg(final String site1, final String site2) throws MXException, RemoteException {
        final String tempString1 = "'" + site1 + "'";
        final String tempString2 = "'" + site2 + "'";
        final String typeWhere = " siteid in (" + tempString1 + ", " + tempString2 + ")";
        final SqlFormat sqf = new SqlFormat(this.getUserInfo(), typeWhere);
        final MboSetRemote siteSet = this.getMboSet("$site", "SITE", sqf.format());
        final MboRemote site1mbo = siteSet.getMbo(0);
        final MboRemote site2mbo = siteSet.getMbo(1);
        final String org1 = site1mbo.getString("orgid");
        final String org2 = site2mbo.getString("orgid");
        return org1.equals(org2);
    }
    
    private InvBalancesRemote getNewInvBalances(final Vector v) throws MXException, RemoteException {
        for (int i = 0; i < v.size(); ++i) {
            final Object obj = v.elementAt(i);
            final InvBalancesRemote invBal = (InvBalancesRemote)obj;
            if (invBal.getString("itemnum").equals(this.getString("itemnum")) && invBal.getString("location").equals(this.getString("tostoreloc")) && invBal.getString("itemsetid").equals(this.getString("itemsetid")) && invBal.getString("binnum").equals(this.getString("tobin")) && invBal.getString("lotnum").equals(this.getString("tolot")) && invBal.getString("siteid").equals(this.getString("siteid")) && invBal.getString("conditioncode").equals(this.getString("conditioncode"))) {
                return invBal;
            }
        }
        return null;
    }
    
    @Override
    public MboRemote getInvCostRecord(final MboRemote toInventory) throws MXException, RemoteException {
        final MboSetRemote invcostSet = toInventory.getMboSet("INVCOST");
        int i = 0;
        MboRemote invCost = null;
        while ((invCost = invcostSet.getMbo(i)) != null) {
            if (invCost.getString("itemnum").equals(this.getString("itemnum")) && invCost.getString("location").equals(this.getString("tostoreloc")) && invCost.getString("itemsetid").equals(this.getString("itemsetid")) && invCost.getString("conditioncode").equals(this.getString("conditioncode")) && invCost.getString("siteid").equals(this.getString("siteid"))) {
                return invCost;
            }
            ++i;
        }
        return null;
    }
    
    private InvBalancesRemote getFromInvBalances(final Vector v) throws MXException, RemoteException {
        for (int i = 0; i < v.size(); ++i) {
            final Object obj = v.elementAt(i);
            final InvBalancesRemote invBal = (InvBalancesRemote)obj;
            if (invBal.getString("itemnum").equals(this.getString("itemnum")) && invBal.getString("location").equals(this.getString("fromstoreloc")) && invBal.getString("binnum").equals(this.getString("frombin")) && invBal.getString("lotnum").equals(this.getString("fromlot")) && invBal.getString("conditioncode").equals(this.getString("fromconditioncode"))) {
                return invBal;
            }
        }
        return null;
    }
    
    private InventoryRemote getNewInventory(final Vector v) throws MXException, RemoteException {
        for (int i = 0; i < v.size(); ++i) {
            final Object obj = v.elementAt(i);
            final InventoryRemote inv = (InventoryRemote)obj;
            if (inv.getString("itemnum").equals(this.getString("itemnum")) && inv.getString("itemsetid").equals(this.getString("itemsetid")) && inv.getString("location").equals(this.getString("tostoreloc")) && inv.getString("siteid").equals(this.getString("siteid"))) {
                return inv;
            }
        }
        return null;
    }
    
    private MboRemote getRelatedInvBalance(final Inventory inventoryMbo, final String checkBinnum, final String checkLotnum, final String checkConditionCode) throws MXException, RemoteException {
        return inventoryMbo.getInvBalanceRecord(checkBinnum, checkLotnum, checkConditionCode);
    }
    
    private boolean newInvLotExists(final Vector v, final String tolot) throws MXException, RemoteException {
        for (int i = 0; i < v.size(); ++i) {
            final Object obj = v.elementAt(i);
            final InvLotRemote invLot = (InvLotRemote)v.elementAt(i);
            if (invLot.getString("lotnum").equals(tolot)) {
                return true;
            }
        }
        return false;
    }
    
    private MboRemote invLotExists() throws MXException, RemoteException {
        final MboRemote poLine = this.getPOLine();
        String location = "";
        if (this.isHolding() && poLine != null && !poLine.getString("storeloc").equals("")) {
            location = poLine.getString("storeloc");
        }
        else {
            location = this.getString("tostoreloc");
            if (location.equals("") && this.isNull("tostoreloc") && !this.isNull("courier") && poLine != null && !poLine.getString("storeloc").equals("")) {
                location = poLine.getString("storeloc");
            }
        }
        final String tolot = this.getString("tolot");
        final String item = this.getString("itemnum");
        final String set = this.getString("itemsetid");
        final String site = this.getString("siteid");
        final SqlFormat sqf = new SqlFormat(this, "lotnum = :1 and itemnum = :2 and location = :3 and itemsetid = :4 and siteid = :5");
        sqf.setObject(1, "INVLOT", "lotnum", tolot);
        sqf.setObject(2, "INVLOT", "itemnum", item);
        sqf.setObject(3, "INVLOT", "location", location);
        sqf.setObject(4, "INVLOT", "itemsetid", set);
        sqf.setObject(5, "INVLOT", "siteid", site);
        final MboSetRemote invLotSet = ((MboSet)this.getThisMboSet()).getSharedMboSet("INVLOT", sqf.format());
        return invLotSet.getMbo(0);
    }
    
    public void updateInventoryCostAndBalances() throws MXException, RemoteException {
        cust.component.Logger.Log("MatRecTrans.updateInventoryCostAnsBalances");
        if (this.isStageTransfer() || this.isShipTransfer() || this.isShipCancel()) {
            return;
        }
        if ((this.getTranslator().toInternalString("ISSUETYP", this.getString("issuetype")).equalsIgnoreCase("VOIDRECEIPT") || this.isVoidShipReceipt()) && !this.isNull("itemnum") && (this.getBoolean("ITEM.rotating") || this.isInspectionRequired())) {
            return;
        }
        final MatRecTransSet matSet = (MatRecTransSet)this.getThisMboSet();
        final Vector invBalVector = matSet.getInvBalVector();
        final Vector invLotVector = matSet.getInvLotVector();
        final Vector inventoryVector = matSet.getInventoryVector();
        final Vector fromInvBalVector = matSet.getfromInvBalVector();
        if (this.toBeDeleted()) {
            return;
        }
        if (this.isKitting()) {
            return;
        }
        if (this.getBoolean("issue")) {
            return;
        }
        if (!this.isNull("tolot") && (this.toBeAdded() || !this.getMboValue("tolot").isReadOnly())) {
            final MboRemote invlot = this.invLotExists();
            if (invlot == null) {
                final String tolot = this.getString("tolot");
                if (!this.newInvLotExists(invLotVector, tolot)) {
                    final InvLotSetRemote invLotSet = (InvLotSetRemote)this.getMboSet("$CreateInvLot" + tolot, "INVLOT", "");
                    final InvLot newInvLot = (InvLot)invLotSet.add();
                    final MboRemote poLine = this.getPOLine();
                    String location = "";
                    if (this.isHolding() && poLine != null && !poLine.getString("storeloc").equals("")) {
                        location = poLine.getString("storeloc");
                        newInvLot.setValue("location", location, 11L);
                    }
                    else if (this.isNull("tostoreloc") && !this.isNull("courier") && poLine != null && !poLine.getString("storeloc").equals("")) {
                        location = poLine.getString("storeloc");
                        newInvLot.setValue("location", location, 11L);
                    }
                    newInvLot.setValue("lotnum", tolot, 11L);
                    newInvLot.setValue("useby", this.getDate("useby"), 11L);
                    newInvLot.setValue("shelflife", this.getString("shelflife"), 11L);
                    newInvLot.setValue("mfglotnum", this.getString("mfglotnum"), 11L);
                    invLotVector.addElement(newInvLot);
                }
            }
            else {
                if (this.getMboValue("useby").isModified()) {
                    invlot.setValue("useby", this.getString("useby"), 11L);
                }
                if (this.getMboValue("shelflife").isModified()) {
                    invlot.setValue("shelflife", this.getString("shelflife"), 11L);
                }
                if (this.getMboValue("mfglotnum").isModified()) {
                    invlot.setValue("mfglotnum", this.getString("mfglotnum"), 11L);
                }
            }
        }
        if (this.isTransfer()) {
            final MboRemote owner = this.getOwner();
            if (owner instanceof LocationRemote) {
                if (!owner.getMboSet("MATRECTRANSOUT").isEmpty() && this.getMboValue("courier").isNull() && this.getMboValue("toStoreloc").isNull()) {
                    final String toLocation = this.getString("toStoreLoc");
                    final String requestingSite = this.getString("newsite");
                    final String supplyingSite = this.getString("fromsiteid");
                    if (!requestingSite.equals(supplyingSite) && !this.inSameOrg(requestingSite, supplyingSite)) {
                        throw new MXApplicationException("inventory", "noCrossOrgInDirectTransfer");
                    }
                    final MboRemote polinembo = this.getPOLine();
                    if (polinembo != null) {
                        final boolean inspectionrequired = polinembo.getBoolean("inspectionrequired");
                        if (inspectionrequired) {
                            throw new MXApplicationException("inventory", "cannotuseinspreqdpoline");
                        }
                    }
                }
                if (!owner.getMboSet("MATRECTRANSIN").isEmpty() && this.getMboValue("courier").isNull() && !this.getMboValue("fromStoreloc").isNull()) {
                    final String fromLocation = this.getString("fromStoreLoc");
                    final String requestingSite = this.getString("siteid");
                    final String supplyingSite = this.getString("fromsiteid");
                    if (!requestingSite.equals(supplyingSite) && !this.inSameOrg(requestingSite, supplyingSite)) {
                        throw new MXApplicationException("inventory", "noCrossOrgInDirectTransfer");
                    }
                    final MboRemote polinembo = this.getPOLine();
                    if (polinembo != null) {
                        final boolean inspectionrequired = polinembo.getBoolean("inspectionrequired");
                        if (inspectionrequired) {
                            throw new MXApplicationException("inventory", "cannotuseinspreqdpoline");
                        }
                    }
                }
            }
        }
        String assetIssueUnit = null;
        if (!this.getMboValue("rotassetnum").isNull()) {
            final AssetSetRemote assetSet = (AssetSetRemote)this.getMboSet("ASSET");
            if (!assetSet.isEmpty()) {
                final AssetRemote asset = (AssetRemote)assetSet.getMbo(0);
                if (!asset.getString("siteid").equals(asset.getString("newsite"))) {
                    final AssetSetRemote movedAssetSet = (AssetSetRemote)asset.getMboSet("MOVEDASSET");
                    if (movedAssetSet.isEmpty()) {
                        asset.checkForNewAssetSite(false);
                    }
                }
                try {
                    assetIssueUnit = asset.getIssueUnitForItem(asset.getString("location"));
                }
                catch (MXApplicationException mxe) {
                    if (!mxe.getErrorKey().equalsIgnoreCase("noInventory")) {
                        throw mxe;
                    }
                    final ItemRemote item = (ItemRemote)this.getMboSet("ITEM").getMbo(0);
                    if (item != null) {
                        assetIssueUnit = item.getString("issueunit");
                    }
                }
            }
            if (assetIssueUnit == null && this.isNull("fromstoreloc") && this.isNull("receivedunit") && this.isTransfer()) {
                final MboRemote owner2 = this.getOwner();
                if (owner2 != null && owner2 instanceof LocationRemote) {
                    final MboRemote ownersOwner = owner2.getOwner();
                    if (ownersOwner != null && ownersOwner instanceof AssetRemote && !ownersOwner.toBeAdded()) {
                        final AssetRemote fromAsset = (AssetRemote)ownersOwner;
                        assetIssueUnit = fromAsset.getIssueUnitForItem(fromAsset.getString("location"));
                    }
                }
            }
        }
        if (!this.getString("ponum").equals("") && this.getString("polinenum").equalsIgnoreCase("")) {
            final Object[] params = { this.getMboValue("polinenum").getName() };
            throw new MXApplicationException("system", "null", params);
        }
        if (this.toBeAdded() && this.getDouble("receiptquantity") >= 0.0 && !this.isNull("status") && this.getTranslator().toInternalString("RECEIPTSTATUS", this.getString("status")).equalsIgnoreCase("WINSP") && !this.isCourierOrLabor()) {
            return;
        }
        final MboRemote PO = this.getPO();
        MboRemote POLine = this.getPOLine();
        if (PO != null && POLine == null && this.isTransfer()) {
            final SqlFormat sqf = new SqlFormat(this, "ponum = :ponum and polinenum = :polinenum and siteid = :siteid");
            final MboSetRemote polineSet = ((MboSet)this.getThisMboSet()).getSharedMboSet("POLINE", sqf.format());
            POLine = polineSet.getMbo(0);
        }
        if ((this.isHolding() || ((this.isReceipt() || this.isTransfer()) && !this.isNull("status") && ((this.getTranslator().toInternalString("RECEIPTSTATUS", this.getString("status")).equalsIgnoreCase("COMP") && !this.isNull("itemnum") && this.getBoolean("ITEM.rotating") && this.isNull("rotassetnum")) || (!this.isNull("itemnum") && this.getBoolean("ITEM.rotating") && !this.isNull("rotassetnum") && !this.getString("siteid").equalsIgnoreCase(this.getString("positeid")))))) && (!this.isHolding() || (!this.getTranslator().toInternalString("ISSUETYP", this.getString("issuetype")).equalsIgnoreCase("RETURN") && !this.getTranslator().toInternalString("ISSUETYP", this.getString("issuetype")).equalsIgnoreCase("SHIPRETURN")) || this.isReject())) {
            if ((!this.isTransfer() || this.isNull("fromstoreloc") || POLine == null || PO.isNull("storeloc") || PO.getString("storelocsiteid").equalsIgnoreCase(POLine.getString("tositeid")) || this.isNull("itemnum") || !this.getBoolean("ITEM.rotating")) && (!this.isTransfer() || this.isNull("shipmentnum")) && (!this.isShipReceipt() || PO == null || this.isInspectionRequired() || !this.getTranslator().toInternalString("RECEIPTSTATUS", this.getString("status")).equalsIgnoreCase("COMP") || !this.getString("siteid").equalsIgnoreCase(this.getString("fromsiteid")))) {
                return;
            }
            if (this.isTransfer() && !this.isNull("fromstoreloc") && POLine != null && !PO.isNull("storeloc") && !PO.getString("storelocsiteid").equalsIgnoreCase(POLine.getString("tositeid")) && !this.isNull("itemnum") && this.getBoolean("ITEM.rotating") && (!this.isTransfer() || this.isNull("shipmentnum")) && (!this.isShipReceipt() || PO == null || this.isInspectionRequired() || !this.getTranslator().toInternalString("RECEIPTSTATUS", this.getString("status")).equalsIgnoreCase("COMP") || !this.getString("siteid").equalsIgnoreCase(this.getString("fromsiteid")))) {
                return;
            }
        }
        if (this.isTransfer() && !this.isNull("rotassetnum") && this.isNull("fromstoreloc")) {
            final MboRemote owner3 = this.getOwner();
            if (owner3 != null && owner3 instanceof LocationRemote) {
                final MboRemote ownerOwner = owner3.getOwner();
                if (ownerOwner != null) {
                    if (ownerOwner instanceof MatRecTransRemote && ownerOwner.toBeAdded() && ownerOwner.getBoolean("ITEM.rotating") && !ownerOwner.isNull("rotassetnum") && ((MatRecTrans)ownerOwner).isTransfer() && !ownerOwner.getString("fromsiteid").equals(ownerOwner.getString("siteid"))) {
                        return;
                    }
                    final MboRemote ownerOwnerOwner = ownerOwner.getOwner();
                    if (ownerOwnerOwner != null && this.isNull("status") && ownerOwner instanceof AssetRemote && (ownerOwnerOwner instanceof MatRecTransRemote || ownerOwnerOwner instanceof AssetInputRemote) && this.getBoolean("ITEM.rotating")) {
                        MboRemote ownerOwnerOwnerOwner = null;
                        if (ownerOwnerOwner != null) {
                            ownerOwnerOwnerOwner = ownerOwnerOwner.getOwner();
                        }
                        if (ownerOwnerOwnerOwner != null && ownerOwnerOwnerOwner.isBasedOn("SHIPMENT")) {
                            return;
                        }
                    }
                }
            }
        }
        if ((this.getTranslator().toInternalString("ISSUETYP", this.getString("issuetype")).equalsIgnoreCase("RETURN") || this.getTranslator().toInternalString("ISSUETYP", this.getString("issuetype")).equalsIgnoreCase("SHIPRETURN")) && this.willBeHolding()) {
            if (this.getTranslator().toInternalString("ISSUETYP", this.getString("issuetype")).equalsIgnoreCase("SHIPRETURN")) {
                final InvReserve invReserveMbo = (InvReserve)this.getInvReserve("ponum=:ponum and polinenum=:polinenum");
                if (invReserveMbo != null) {
                    invReserveMbo.setValue("shippedqty", invReserveMbo.getDouble("shippedqty") + this.getDouble("receiptquantity"), 2L);
                }
            }
            if (this.getTranslator().toInternalString("ISSUETYP", this.getString("issuetype")).equalsIgnoreCase("RETURN")) {
                return;
            }
        }
        if (this.toBeAdded() || this.isApprovingReceipt()) {
            final double quantity = this.getDouble("quantity");
            final double curBal = 0.0;
            final String tobin = this.getString("tobin");
            final String tolot2 = this.getString("tolot");
            boolean newInventory = false;
            double conversionFactor = this.getDouble("conversion");
            if (conversionFactor == 0.0) {
                conversionFactor = 1.0;
            }
            final String toConditionCode = this.getString("conditionCode");
            MboRemote origReceipt = null;
            if (this.isShipReturn() || ((this.isTransfer() || this.isReturn() || this.isShipReturn()) && !this.isNull("fromstoreloc") && (!this.isHolding() & !this.willBeHolding()) && (this.getString("siteid").equalsIgnoreCase(this.getString("fromsiteid")) || (this.getOwner() != null && (this.getOwner() instanceof LocationRemote || this.getOwner() instanceof InventoryRemote || this.getOwner() instanceof InvUseRemote)) || this.getOwner() == null))) {
                Inventory fromInventoryMbo;
                if (this.isShipReturn() && this.willBeHolding()) {
                    origReceipt = this.getReceiptForThisReturn();
                    if (origReceipt != null) {
                        fromInventoryMbo = (Inventory)this.getSharedInventory(origReceipt.getString("fromstoreloc"), origReceipt.getString("fromsiteid"));
                    }
                    else {
                        fromInventoryMbo = (Inventory)this.getSharedInventory(this.getString("tostoreloc"), this.getString("siteid"));
                    }
                }
                else {
                    fromInventoryMbo = (Inventory)this.getSharedInventory(this.getString("fromstoreloc"), this.getString("fromsiteid"));
                }
                if (this.isNull("receivedunit")) {
                    this.setValue("receivedunit", fromInventoryMbo.getString("issueunit"), 11L);
                }
                if (this.isNull("courier") || this.isNull("tostoreloc")) {
                    if (!this.useIntegration(fromInventoryMbo, "INVTR")) {
                        String frombin;
                        String fromlot;
                        String fromConditionCode;
                        if (this.isShipReturn() && origReceipt != null) {
                            frombin = origReceipt.getString("frombin");
                            fromlot = origReceipt.getString("fromlot");
                            fromConditionCode = origReceipt.getString("fromConditionCode");
                        }
                        else {
                            frombin = this.getString("frombin");
                            fromlot = this.getString("fromlot");
                            fromConditionCode = this.getString("fromConditionCode");
                        }
                        InvBalancesRemote fromInvBal = this.getFromInvBalances(fromInvBalVector);
                        if (fromInvBal == null) {
                            if (this.getString("fromstoreloc").equals(this.getString("tostoreloc")) && frombin.toUpperCase().equals(tobin.toUpperCase()) && fromlot.toUpperCase().equals(tolot2.toUpperCase())) {
                                final int i = matSet.getCurrentPosition();
                                fromInventoryMbo.triggerNewRelationship(i + 1);
                            }
                            else {
                                final InvBalancesRemote differenceCaseBins = this.getFromInvBalancesCaseDifference(fromInvBalVector);
                                if (differenceCaseBins != null) {
                                    final int j = fromInventoryMbo.getNewRelationshipIndicator();
                                    fromInventoryMbo.triggerNewRelationship(j + 1);
                                }
                            }
                            fromInvBal = (InvBalancesRemote)this.getRelatedInvBalance(fromInventoryMbo, frombin, fromlot, fromConditionCode);
                            fromInvBalVector.addElement(fromInvBal);
                        }
                        if (!this.getFromInvBalUpdated()) {
                            double fromOldBal = 0.0;
                            if (this.useInitialCurBal) {
                                fromOldBal = fromInvBal.getMboInitialValue("curbal").asDouble();
                            }
                            else {
                                fromOldBal = fromInvBal.getDouble("curbal");
                            }
                            fromInvBal.updateCurrentBalance(quantity / conversionFactor * -1.0 + fromOldBal);
                            this.setFromInvBalUpdated();
                        }
                        if (!this.invReserveUpdated) {
                            final InvReserve invReserveMbo2 = (InvReserve)this.getInvReserve("ponum=:ponum and polinenum=:polinenum");
                            if (invReserveMbo2 != null && !this.willBeHolding()) {
                                invReserveMbo2.incrActualQty(quantity);
                            }
                        }
                        fromInventoryMbo = (Inventory)this.getSharedInventory(this.getString("fromstoreloc"), this.getString("fromsiteid"));
                        if (fromInventoryMbo != null && this.getFromInvBalUpdated() && this.isShipReturn() && (fromInventoryMbo.getCostType().equals("LIFO") || fromInventoryMbo.getCostType().equals("FIFO"))) {
                            fromInventoryMbo.addInvLifoFifoCostRecord(MXMath.abs(this.getDouble("quantity")), this.getDouble("unitcost"), this.getString("conditioncode"), this.getName(), this.getLong("matrectransid"));
                        }
                    }
                }
            }
            else if (this.toBeAdded() && this.isShipReceipt() && !this.isNull("ponum")) {
                if (!this.invReserveUpdated) {
                    final InvReserve invReserveMbo3 = (InvReserve)this.getInvReserve("ponum=:ponum and polinenum=:polinenum");
                    if (invReserveMbo3 != null) {
                        invReserveMbo3.incrActualQty(quantity);
                    }
                }
            }
            else if (this.toBeAdded() && this.isTransfer() && !this.isNull("ponum") && !this.isNull("shipmentnum") && this.getReceiptStatus().equalsIgnoreCase("COMP") && !this.invReserveUpdated) {
                final InvReserve invReserveMbo3 = (InvReserve)this.getInvReserve("ponum=:ponum and polinenum=:polinenum");
                if (invReserveMbo3 != null) {
                    invReserveMbo3.setValue("shippedqty", invReserveMbo3.getDouble("shippedqty") - MXMath.divide(quantity, conversionFactor), 2L);
                    invReserveMbo3.incrActualQty(MXMath.divide(quantity, conversionFactor));
                }
            }
            if (this.isTransfer()) {
                final MboRemote owner4 = this.getOwner();
                if (owner4 instanceof LocationRemote && !owner4.getMboSet("MATRECTRANSOUT").isEmpty() && !this.getMboValue("courier").isNull() && this.getMboValue("toStoreloc").isNull()) {
                    return;
                }
            }
            if (this.isShipReturn() && this.willBeHolding()) {
                return;
            }
            Inventory toInventoryMbo = (Inventory)this.getSharedInventory(this.getString("tostoreloc"), this.getString("siteid"));
            if (toInventoryMbo == null) {
                toInventoryMbo = (Inventory)this.getNewInventory(inventoryVector);
                if (toInventoryMbo == null) {
                    final InventorySetRemote invSet = (InventorySetRemote)this.getMboSet("NEW_INVENTORY");
                    toInventoryMbo = (Inventory)invSet.add();
                    newInventory = true;
                    final Inventory fromInventoryMbo2 = (Inventory)this.getSharedInventory(this.getString("fromstoreloc"), this.getString("fromsiteid"));
                    if (fromInventoryMbo2 != null) {
                        toInventoryMbo.setValue("Manufacturer", fromInventoryMbo2.getString("Manufacturer"), 2L);
                        toInventoryMbo.setValue("Modelnum", fromInventoryMbo2.getString("Modelnum"), 2L);
                        toInventoryMbo.setValue("status", fromInventoryMbo2.getString("status"), 2L);
                        if (toInventoryMbo.isNull("orderunit")) {
                            toInventoryMbo.setValue("orderunit", fromInventoryMbo2.getString("orderunit"), 11L);
                        }
                    }
                    if (this.isNull("receivedunit")) {
                        final MboRemote poline = this.getPOLine();
                        if (poline != null) {
                            toInventoryMbo.setValue("issueunit", poline.getString("orderunit"));
                        }
                        else {
                            final SqlFormat sqf2 = new SqlFormat(this, "itemnum=:itemnum and itemsetid=:itemsetid ");
                            final MboSetRemote inventorySet = this.getMboSet("$invset", "INVENTORY", sqf2.format());
                            if (inventorySet.isEmpty()) {
                                final Object[] param = { this.getString("itemnum"), this.getString("tostoreloc") };
                                throw new MXApplicationException("asset", "noInventory", param);
                            }
                            final MboRemote inventory = inventorySet.getMbo(0);
                            assetIssueUnit = inventory.getString("issueunit");
                            toInventoryMbo.setValue("issueunit", assetIssueUnit);
                        }
                    }
                    else {
                        toInventoryMbo.setValue("issueunit", this.getString("receivedunit"));
                    }
                    toInventoryMbo.setValue("itemnum", this.getString("itemnum"));
                    toInventoryMbo.setPropagateKeyFlag(false);
                    toInventoryMbo.setValue("siteid", this.getString("siteid"));
                    toInventoryMbo.setPropagateKeyFlag(true);
                    toInventoryMbo.setValue("location", this.getString("tostoreloc"));
                    toInventoryMbo.setValue("conditionCode", this.getString("conditioncode"), 2L);
                    final LocationSetRemote destLocMbo = (LocationSetRemote)this.getMboSet("LOCATIONS");
                    if (destLocMbo.isEmpty()) {
                        final String[] params2 = { this.getString("tostoreloc"), this.getString("newsite") };
                        throw new MXApplicationException("locations", "invalidlocationsite", params2);
                    }
                    final LocationRemote toLoc = (LocationRemote)destLocMbo.getMbo(0);
                    if (this.getTranslator().toInternalString("LINETYPE", this.getString("linetype")).equals("TOOL")) {
                        toInventoryMbo.setValue("controlacc", toLoc.getString("toolcontrolacc"), 2L);
                    }
                    else {
                        toInventoryMbo.setValue("controlacc", toLoc.getString("controlacc"), 2L);
                    }
                    toInventoryMbo.setValue("shrinkageacc", toLoc.getString("shrinkageacc"), 2L);
                    toInventoryMbo.setValue("invcostadjacc", toLoc.getString("invcostadjacc"), 2L);
                    toInventoryMbo.setValue("binnum", this.getString("tobin"), 2L);
                    toInventoryMbo.setValue("curbal", this.getDouble("quantity"), 2L);
                    toInventoryMbo.setValue("stdcost", this.getDouble("unitcost"), 2L);
                    if (this.isShipReceipt()) {
                        toInventoryMbo.setValue("avgcost", this.getDouble("loadedcost") / this.getDouble("exchangerate") / quantity, 2L);
                    }
                    else {
                        toInventoryMbo.setValue("avgcost", this.getDouble("loadedcost") / quantity, 2L);
                    }
                    if (toInventoryMbo.getDouble("avgcost") == 0.0) {
                        toInventoryMbo.setValue("avgcost", toInventoryMbo.getDouble("stdcost"), 11L);
                    }
                    final MboRemote poRemote = this.getPO();
                    if (poRemote != null) {
                        toInventoryMbo.setValue("vendor", poRemote.getString("vendor"));
                    }
                    final ItemRemote item2 = (ItemRemote)this.getMboSet("ITEM").getMbo(0);
                    if (item2 != null) {
                        if (item2.isLotted()) {
                            toInventoryMbo.setValue("lotnum", this.getString("tolot"));
                        }
                        if (item2.isConditionEnabled()) {
                            final double temp = this.getDouble("condrate");
                            final String condition = this.getString("conditioncode");
                            if (this.isNull("condrate")) {
                                this.setValue("condrate", toInventoryMbo.getDouble("condrate"), 11L);
                            }
                            if (this.getDouble("condrate") < 100.0) {
                                final MboRemote itemCond = item2.getOneHundredPercent();
                                if (itemCond != null) {
                                    final MboRemote invCost = toInventoryMbo.getMboSet("INVCOST").add();
                                    invCost.setValue("conditioncode", itemCond.getString("conditioncode"), 11L);
                                    invCost.setValue("condrate", 100, 11L);
                                    final double ratio = this.getDouble("condrate") / 100.0;
                                    if (ratio != 0.0) {
                                        invCost.setValue("stdcost", toInventoryMbo.getDouble("stdcost") / ratio, 2L);
                                    }
                                    else {
                                        invCost.setValue("stdcost", this.getDouble("unitcost"), 2L);
                                    }
                                }
                            }
                        }
                    }
                    final MboRemote poLine2 = this.getPOLine();
                    if (poLine2 != null) {
                        toInventoryMbo.setValue("manufacturer", poLine2.getString("manufacturer"), 2L);
                        toInventoryMbo.setValue("modelnum", poLine2.getString("modelnum"), 2L);
                        toInventoryMbo.setValue("catalogcode", poLine2.getString("catalogcode"), 2L);
                        toInventoryMbo.setValue("orderunit", poLine2.getString("orderunit"));
                    }
                    toInventoryMbo.setCostType();
                    inventoryVector.addElement(toInventoryMbo);
                    toInventoryMbo.setAutoCreateInvBalances(false);
                    toInventoryMbo.setAutoCreateInvCost(false);
                }
                else {
                    newInventory = false;
                    toInventoryMbo.setValue("conditionCode", this.getString("conditioncode"), 2L);
                }
            }
            if (toInventoryMbo != null) {
                String pcid = "RCINV";
                if (this.isMisclReceipt()) {
                    pcid = "INV";
                }
                if (this.useIntegration(toInventoryMbo, pcid)) {
                    return;
                }
            }
            final String costType = toInventoryMbo.getCostType();
            MboRemote invcost = this.getInvCostRecord(toInventoryMbo);
            if (!costType.equals("LIFO") && !costType.equals("FIFO") && invcost == null) {
                invcost = toInventoryMbo.addInvCostRecord(this.getString("conditioncode"));
                invcost.setValue("stdcost", this.getDouble("unitcost"), 2L);
            }
            final InvBalancesRemote invBalRelated = (InvBalancesRemote)this.getRelatedInvBalance(toInventoryMbo, tobin, tolot2, toConditionCode);
            boolean updateInventory = false;
            final String updateInvSetting = this.getMboServer().getMaxVar().getString("UPDATEINVENTORY", this.getString("orgid"));
            if (updateInvSetting.equals("1")) {
                updateInventory = true;
            }
            if (invBalRelated != null) {
                if ((!this.getToInvBalUpdated() && (!this.isTransfer() || this.isNull("receiptref") || !this.isNull("rotassetnum") || this.isNull("itemnum") || !this.getBoolean("ITEM.rotating") || !this.getString("siteid").equalsIgnoreCase(this.getString("positeid")) || this.getReceiptForThisReturn() == null || !this.getReceiptForThisReturn().getString("fromsiteid").equalsIgnoreCase(this.getReceiptForThisReturn().getString("siteid")))) || (this.isTransfer() && !this.isNull("itemnum") && this.getBoolean("ITEM.rotating") && this.getReceiptStatus().equalsIgnoreCase("COMP") && !this.isNull("shipmentnum"))) {
                    double toOldBal = invBalRelated.getMboInitialValue("curbal").asDouble();
                    if (!this.useInitialCurBal) {
                        toOldBal = invBalRelated.getDouble("curbal");
                    }
                    this.setValue("curbal", toOldBal, 2L);
                    if (this.isReturn() || this.isShipReturn()) {
                        final double avblQty = toInventoryMbo.getDouble("avblBalance");
                        final InventoryService intServ = (InventoryService)((AppService)this.getMboServer()).getMXServer().lookup("INVENTORY");
                        intServ.canGoNegative(this.getUserInfo(), Math.abs(this.getDouble("quantity")), invBalRelated.getDouble("curbal"), avblQty, this);
                    }
                    if (toInventoryMbo.getAccumulativeTotalCurBal() != -999.99) {
                        this.setValue("totalcurbal", toInventoryMbo.getAccumulativeTotalCurBal(), 2L);
                        toInventoryMbo.increaseAccumulativeTotalCurBal(this.getDouble("quantity"));
                    }
                    else {
                        final double totalCurBal = toInventoryMbo.getCurrentBalance(null, null);
                        this.setValue("totalcurbal", totalCurBal, 2L);
                        toInventoryMbo.increaseAccumulativeTotalCurBal(totalCurBal + this.getDouble("quantity"));
                    }
                    if (!costType.equals("LIFO") && !costType.equals("FIFO")) {
                        if (this.getTranslator().toInternalString("ISSUETYP", this.getString("issuetype")).equalsIgnoreCase("INVOICE") && !newInventory) {
                            if (updateInventory) {
                                this.updateInventory(toInventoryMbo, this.getPO(), (InvCost)invcost);
                            }
                        }
                        else if (!newInventory) {
                            this.updateInventory(toInventoryMbo, this.getPO(), (InvCost)invcost);
                        }
                    }
                    invBalRelated.updateCurrentBalance(quantity + toOldBal);
                    this.setToInvBalUpdated();
                    if (!costType.equals("LIFO") && !costType.equals("FIFO")) {
                        ((InvCost)invcost).increaseAccumulativeReceiptQty(quantity);
                    }
                }
            }
            else {
                InvBalancesRemote invBal = this.getNewInvBalances(invBalVector);
                if (invBal != null) {
                    if (!this.getToInvBalUpdated() && !newInventory) {
                        this.setValue("curbal", invBal.getCurrentBalance(), 2L);
                        this.setValue("totalcurbal", toInventoryMbo.getCurrentBalance(null, null), 2L);
                        if (!costType.equals("LIFO") && !costType.equals("FIFO")) {
                            if (this.getTranslator().toInternalString("ISSUETYP", this.getString("issuetype")).equalsIgnoreCase("INVOICE") && !newInventory) {
                                if (updateInventory) {
                                    this.updateInventory(toInventoryMbo, this.getPO(), (InvCost)invcost);
                                }
                            }
                            else if (!newInventory) {
                                this.updateInventory(toInventoryMbo, this.getPO(), (InvCost)invcost);
                            }
                        }
                        invBal.updateCurrentBalance(quantity + this.getDouble("curbal"));
                        this.setToInvBalUpdated();
                        if (!costType.equals("LIFO") && !costType.equals("FIFO")) {
                            ((InvCost)invcost).increaseAccumulativeReceiptQty(quantity);
                        }
                    }
                }
                else {
                    MboRemote receipt = null;
                    if (this.isReturn() || this.isShipReturn()) {
                        final InventoryService intServ2 = (InventoryService)((AppService)this.getMboServer()).getMXServer().lookup("INVENTORY");
                        try {
                            intServ2.canGoNegative(this.getUserInfo(), Math.abs(quantity), 0.0, 0.0, this);
                        }
                        catch (MXApplicationException mxe2) {
                            if (mxe2.getErrorKey().equalsIgnoreCase("negativeBalisNotAllowed")) {
                                this.delete();
                            }
                            throw mxe2;
                        }
                    }
                    if (this.isTransfer() && this.isNull("rotassetnum") && !this.isNull("receiptref")) {
                        receipt = this.getReceiptForThisReturn();
                    }
                    if (!this.isTransfer() || this.isNull("receiptref") || !this.isNull("rotassetnum") || this.isNull("itemnum") || !this.getBoolean("ITEM.rotating") || this.getString("siteid").equalsIgnoreCase(this.getString("positeid")) || (receipt != null && receipt.getString("fromsiteid").equalsIgnoreCase(this.getString("siteid")))) {
                        invBal = toInventoryMbo.addInvBalancesRecord(tobin, tolot2, quantity, this.getString("conditioncode"));
                        this.setToInvBalUpdated();
                        if (!costType.equals("LIFO") && !costType.equals("FIFO")) {
                            if (this.getTranslator().toInternalString("ISSUETYP", this.getString("issuetype")).equalsIgnoreCase("INVOICE")) {
                                if (updateInventory) {
                                    this.updateInventory(toInventoryMbo, this.getPO(), (InvCost)invcost);
                                }
                            }
                            else if (toInventoryMbo.toBeAdded()) {
                                if (toInventoryMbo.getInvCostRecord(this.getString("conditioncode")) != null) {
                                    this.updateInventory(toInventoryMbo, this.getPO(), (InvCost)invcost);
                                }
                            }
                            else {
                                this.updateInventory(toInventoryMbo, this.getPO(), (InvCost)invcost);
                            }
                        }
                    }
                    else if (this.isTransfer() && !this.isNull("receiptref") && this.isNull("rotassetnum") && (!this.isNull("shipmentnum") || this.isNull("itemnum") || !this.getBoolean("ITEM.rotating"))) {
                        invBal = toInventoryMbo.addInvBalancesRecord(tobin, tolot2, quantity, this.getString("conditioncode"));
                        if (!costType.equals("LIFO") && !costType.equals("FIFO")) {
                            if (toInventoryMbo.toBeAdded()) {
                                if (toInventoryMbo.getInvCostRecord(this.getString("conditioncode")) != null) {
                                    this.updateInventory(toInventoryMbo, this.getPO(), (InvCost)invcost);
                                }
                            }
                            else {
                                this.updateInventory(toInventoryMbo, this.getPO(), (InvCost)invcost);
                            }
                        }
                    }
                    if (invBal != null && (!this.isTransfer() || this.isNull("receiptref") || !this.isNull("rotassetnum") || this.isNull("itemnum") || !this.getBoolean("ITEM.rotating") || !this.getString("siteid").equalsIgnoreCase(this.getString("positeid")) || this.getReceiptForThisReturn() == null || !this.getReceiptForThisReturn().getString("fromsiteid").equalsIgnoreCase(this.getReceiptForThisReturn().getString("siteid")))) {
                        invBal.updateCurrentBalance(quantity);
                        invBal.setValue("RECONCILED", true, 2L);
                        invBalVector.addElement(invBal);
                        invBal.setAutoCreateInvLot(false);
                        this.setValue("curbal", 0.0, 2L);
                        this.setValue("totalcurbal", toInventoryMbo.getCurrentBalance(null, null), 2L);
                        this.setToInvBalUpdated();
                        if (!costType.equals("LIFO") && !costType.equals("FIFO")) {
                            ((InvCost)invcost).increaseAccumulativeReceiptQty(quantity);
                        }
                    }
                }
            }
            if (this.getToInvBalUpdated() && (costType.equals("LIFO") || costType.equals("FIFO"))) {
                if (this.getDouble("quantity") > 0.0) {
                    double unitcost = this.getDouble("unitcost");
                    if (this.getDouble("exchangerate") > 1.0) {
                        unitcost = MXMath.multiply(this.getDouble("unitcost"), this.getDouble("exchangerate"));
                    }
                    toInventoryMbo.addInvLifoFifoCostRecord(this.getDouble("quantity"), unitcost, this.getString("conditioncode"), this.getName(), this.getLong("matrectransid"));
                    toInventoryMbo.setAutoCreateInvLifoFifoCost(false);
                }
                else if (this.getDouble("quantity") < 0.0) {
                    final MboRemote receiptMbo = this.getReceiptForThisReturn();
                    if (receiptMbo != null) {
                        toInventoryMbo.consumeInvLifoFifoCostRecord(this.getDouble("quantity"), this.getString("conditioncode"), receiptMbo.getLong("matrectransid"));
                    }
                    else {
                        toInventoryMbo.consumeInvLifoFifoCostRecord(this.getDouble("quantity"), this.getString("conditioncode"));
                    }
                }
            }
        }
    }
    
    public boolean isSwitchoffWOUpdate() {
        return this.switchoffWOUpdate;
    }
    
    @Override
    public void setSwitchoffWOUpdate(final boolean switchoffWOUpdate) {
        this.switchoffWOUpdate = switchoffWOUpdate;
    }
    
    public void setInvReserveUpdatedFlag(final boolean updated) {
        this.invReserveUpdated = updated;
    }
    
    public void setCheckNegBalance(final boolean updated) throws MXException, RemoteException {
        this.checkNegBalance = updated;
    }
    
    boolean getCheckNegBalance() throws MXException, RemoteException {
        return this.checkNegBalance;
    }
    
    public boolean getPOLineUpdated() throws MXException, RemoteException {
        return this.poLineUpdated;
    }
    
    public void setPOLineUpdated() throws MXException, RemoteException {
        this.poLineUpdated = true;
    }
    
    public boolean isStageTransfer() throws MXException, RemoteException {
        return !this.isNull("ISSUETYPE") && this.getTranslator().toInternalString("ISSUETYP", this.getString("ISSUETYPE")).equalsIgnoreCase("STAGETRANSFER");
    }
    
    public boolean isShipTransfer() throws MXException, RemoteException {
        return !this.isNull("ISSUETYPE") && this.getTranslator().toInternalString("ISSUETYP", this.getString("ISSUETYPE")).equalsIgnoreCase("SHIPTRANSFER");
    }
    
    public boolean isShipCancel() throws MXException, RemoteException {
        return !this.isNull("ISSUETYPE") && this.getTranslator().toInternalString("ISSUETYP", this.getString("ISSUETYPE")).equalsIgnoreCase("SHIPCANCEL");
    }
    
    public boolean isShipReturn() throws MXException, RemoteException {
        return !this.isNull("ISSUETYPE") && this.getTranslator().toInternalString("ISSUETYP", this.getString("ISSUETYPE")).equalsIgnoreCase("SHIPRETURN");
    }
    
    public boolean isVoidShipReceipt() throws MXException, RemoteException {
        return !this.isNull("ISSUETYPE") && this.getTranslator().toInternalString("ISSUETYP", this.getString("ISSUETYPE")).equalsIgnoreCase("VOIDSHIPRECEIPT");
    }
    
    public void createMatRecTransRecordforLifoFifo(final MboRemote inv) throws MXException, RemoteException {
        cust.component.Logger.Log("MatRecTrans.createMatRecTransRecordforLifoFifo");
        MboRemote invlifofifocost = null;
        double qty = this.getDouble("receiptquantity");
        final String conditionCode = this.getString("conditionCode");
        final MboSetRemote invlifofifocostset = ((Inventory)inv).getInvLifoFifoCostRecordSetSorted(conditionCode);
        if (invlifofifocostset.isEmpty()) {
            throw new MXApplicationException("inventory", "missingQuantity");
        }
        int i = 0;
        MatRecTrans newMatMbo = this;
        newMatMbo.setValue("split", true, 2L);
        while ((invlifofifocost = invlifofifocostset.getMbo(i)) != null) {
            if (invlifofifocost.getDouble("quantity") == 0.0) {
                ++i;
            }
            else {
                if (invlifofifocost.getDouble("quantity") == qty) {
                    newMatMbo.setValue("receiptquantity", invlifofifocost.getDouble("quantity"), 2L);
                    newMatMbo.setValue("unitcost", invlifofifocost.getDouble("unitcost"), 2L);
                    invlifofifocost.setValue("quantity", 0, 11L);
                    newMatMbo.setValue("actualcost", newMatMbo.getDouble("unitcost"), 2L);
                    newMatMbo.setValue("currencyunitcost", newMatMbo.getDouble("unitcost"), 2L);
                    break;
                }
                if (invlifofifocost.getDouble("quantity") > qty) {
                    newMatMbo.setValue("receiptquantity", qty, 2L);
                    newMatMbo.setValue("unitcost", invlifofifocost.getDouble("unitcost"), 2L);
                    qty = MXMath.subtract(invlifofifocost.getDouble("quantity"), qty);
                    invlifofifocost.setValue("quantity", qty, 11L);
                    newMatMbo.setValue("actualcost", newMatMbo.getDouble("unitcost"), 2L);
                    newMatMbo.setValue("currencyunitcost", newMatMbo.getDouble("unitcost"), 2L);
                    break;
                }
                newMatMbo.setValue("receiptquantity", invlifofifocost.getDouble("quantity"), 2L);
                newMatMbo.setValue("unitcost", invlifofifocost.getDouble("unitcost"), 2L);
                qty = MXMath.subtract(qty, invlifofifocost.getDouble("quantity"));
                invlifofifocost.setValue("quantity", 0, 11L);
                newMatMbo.setValue("actualcost", newMatMbo.getDouble("unitcost"), 2L);
                newMatMbo.setValue("currencyunitcost", newMatMbo.getDouble("unitcost"), 2L);
                newMatMbo = (MatRecTrans)newMatMbo.copy();
                ++i;
            }
        }
    }
    
    @Override
    public boolean canBeReturned() throws MXException, RemoteException {
        final String assetNum = this.getString("rotassetnum");
        final Asset asset = (Asset)this.getMboSet("ASSET").getMbo(0);
        if (asset != null && asset.getBoolean("moved")) {
            return false;
        }
        final POLineRemote poline = this.getPOLine();
        if (poline != null && !poline.getBoolean("issue")) {
            if (asset != null && !asset.getString("location").equalsIgnoreCase(poline.getString("storeloc"))) {
                return false;
            }
            final MboRemote originalReceipt = this.getReceiptForThisReturn();
            if (originalReceipt != null && originalReceipt.getDouble("unitcost") != asset.getDouble("invcost")) {
                return false;
            }
        }
        return true;
    }
    
    public void setShipmentMap() throws MXException, RemoteException {
        final HashMap<String, Long> shipReceiptCountMap = ((MatRecTransSet)this.getThisMboSet()).getShipReceiptCountMap();
        final HashMap<String, Long> voidShipReceiptCountMap = ((MatRecTransSet)this.getThisMboSet()).getVoidShipReceiptCountMap();
        if (!this.getString("shipmentlinenum").equalsIgnoreCase("") && !this.getString("rotassetnum").equalsIgnoreCase("") && this.isShipReceipt()) {
            long receiptcount = 1L;
            if (shipReceiptCountMap != null && shipReceiptCountMap.containsKey(this.getString("shipmentlinenum"))) {
                receiptcount += shipReceiptCountMap.get(this.getString("shipmentlinenum"));
            }
            ((MatRecTransSet)this.getThisMboSet()).setShipReceiptCountMap(this.getString("shipmentlinenum"), Long.valueOf(receiptcount));
            ((MatRecTransSet)this.getThisMboSet()).setLineNumAssetMap(this.getString("shipmentlinenum"), this.getString("rotassetnum"));
        }
        if (!this.getString("shipmentlinenum").equalsIgnoreCase("") && !this.getString("rotassetnum").equalsIgnoreCase("") && this.isVoidShipReceipt()) {
            long voidcount = 1L;
            if (voidShipReceiptCountMap != null && voidShipReceiptCountMap.containsKey(this.getString("shipmentlinenum"))) {
                voidcount += voidShipReceiptCountMap.get(this.getString("shipmentlinenum"));
            }
            ((MatRecTransSet)this.getThisMboSet()).setVoidShipReceiptCountMap(this.getString("shipmentlinenum"), Long.valueOf(voidcount));
        }
    }
    
    private InvBalancesRemote getFromInvBalancesCaseDifference(final Vector v) throws MXException, RemoteException {
        for (int i = 0; i < v.size(); ++i) {
            final Object obj = v.elementAt(i);
            final InvBalancesRemote invBal = (InvBalancesRemote)obj;
            if (invBal.getString("itemnum").equals(this.getString("itemnum")) && invBal.getString("location").equals(this.getString("fromstoreloc")) && invBal.getString("binnum").equalsIgnoreCase(this.getString("frombin")) && invBal.getString("lotnum").equalsIgnoreCase(this.getString("fromlot")) && invBal.getString("conditioncode").equalsIgnoreCase(this.getString("fromconditioncode"))) {
                return invBal;
            }
        }
        return null;
    }
}