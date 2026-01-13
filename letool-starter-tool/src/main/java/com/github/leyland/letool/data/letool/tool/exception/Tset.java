package com.github.leyland.letool.data.letool.tool.exception;

/**
 * @ClassName <h2>Tset</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class Tset {

    public static void main(String[] args) {

        System.out.println(StopCodes$StopCode.error.getRelativeEnum(2));
    }

    public enum StopCodes$StopCode {
        error(0),
        RecentMerge(1),
        BadAddress(2),
        SuspectedDup(3),
        IDNotScanned(5),
        ExpiredID(6),
        NotUsed(9),
        Restrictions(10),
        CasinoRest(12),
        CardChange(13),
        WinLossSent(15),
        AbandonedCard(18),
        PINLocked(20),
        NoPIN(21),
        ActiveFollowUp(22),
        CardInUse(23),
        Birthday(24),
        IncompleteCTR(25),
        Inhouse(28),
        MergePending(29),
        MergeFailed(30),
        IDUnverified(31),
        IDExpiresSoon(33),
        DiscreetPlayer(34),
        SpecialBetLimitsActive(35),
        PromotionalAwards(36),
        ReturnedItem(39),
        RefusedID(40),
        NoID(41),
        PreCommitmentLimit(42),
        SmartCardDisabled(43),
        SelfExcludedRestriction(45),
        CashlessAccountHolder(46),
        DiscreetPlayerLimitedLevel(47);

        private final Integer StopCodeNumber;

        private StopCodes$StopCode(Integer _in) {
            this.StopCodeNumber = _in;
        }

        public Integer getStopCodeNumber() {
            return this.StopCodeNumber;
        }

        public StopCodes$StopCode getRelativeEnum(Integer _in) {
            StopCodes$StopCode[] var4;
            int var3 = (var4 = values()).length;

            for(int var2 = 0; var2 < var3; ++var2) {
                StopCodes$StopCode _e = var4[var2];
                if (_e.StopCodeNumber.equals(_in)) {
                    return _e;
                }
            }

            return error;
        }
    }

}
