package jdk7.lang.reflect;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import sun.security.action.GetBooleanAction;

public class ProxyGenerator {

    private static final int CLASSFILE_MAJOR_VERSION = 49;    //主版本号
    private static final int CLASSFILE_MINOR_VERSION = 0;     //次版本号

    /* 常量池中的常量表 */
    private static final int CONSTANT_UTF8              = 1;  //UTF-8编码的Unicode字符串
    private static final int CONSTANT_UNICODE           = 2;  //Unicode编码的字符串
    private static final int CONSTANT_INTEGER           = 3;  //int类型的字面值
    private static final int CONSTANT_FLOAT             = 4;  //float类型的字面值
    private static final int CONSTANT_LONG              = 5;  //long类型的字面值
    private static final int CONSTANT_DOUBLE            = 6;  //double类型的字面值
    private static final int CONSTANT_CLASS             = 7;  //对一个类或接口的符号引用
    private static final int CONSTANT_STRING            = 8;  //String类型字面值的引用
    private static final int CONSTANT_FIELD             = 9;  //对一个字段的符号引用
    private static final int CONSTANT_METHOD            = 10; //对一个类中方法的符号引用
    private static final int CONSTANT_INTERFACEMETHOD   = 11; //对一个接口中方法的符号引用
    private static final int CONSTANT_NAMEANDTYPE       = 12; //对一个字段或方法的部分符号引用

    /* 修饰符标志 */
    private static final int ACC_PUBLIC                 = 0x00000001;
    private static final int ACC_PRIVATE                = 0x00000002;
//  private static final int ACC_PROTECTED              = 0x00000004;
    private static final int ACC_STATIC                 = 0x00000008;
    private static final int ACC_FINAL                  = 0x00000010;
//  private static final int ACC_SYNCHRONIZED           = 0x00000020;
//  private static final int ACC_VOLATILE               = 0x00000040;
//  private static final int ACC_TRANSIENT              = 0x00000080;
//  private static final int ACC_NATIVE                 = 0x00000100;
//  private static final int ACC_INTERFACE              = 0x00000200;
//  private static final int ACC_ABSTRACT               = 0x00000400;
    private static final int ACC_SUPER                  = 0x00000020;
//  private static final int ACC_STRICT                 = 0x00000800;

    /* 操作指令集 */
//  private static final int opc_nop                    = 0;     //什么都不做
    private static final int opc_aconst_null            = 1;     //将null推送至栈顶
//  private static final int opc_iconst_m1              = 2;     //将int型-1推送至栈顶
    private static final int opc_iconst_0               = 3;     //将int型0推送至栈顶
//  private static final int opc_iconst_1               = 4;     //将int型1推送至栈顶
//  private static final int opc_iconst_2               = 5;     //将int型2推送至栈顶
//  private static final int opc_iconst_3               = 6;     //将int型3推送至栈顶
//  private static final int opc_iconst_4               = 7;     //将int型4推送至栈顶
//  private static final int opc_iconst_5               = 8;     //将int型5推送至栈顶
//  private static final int opc_lconst_0               = 9;     //将long型0推送至栈顶
//  private static final int opc_lconst_1               = 10;    //将long型1推送至栈顶
//  private static final int opc_fconst_0               = 11;    //将float型0推送至栈顶
//  private static final int opc_fconst_1               = 12;    //将float型1推送至栈顶
//  private static final int opc_fconst_2               = 13;    //将float型2推送至栈顶
//  private static final int opc_dconst_0               = 14;    //将double型0推送至栈顶
//  private static final int opc_dconst_1               = 15;    //将double型1推送至栈顶
    private static final int opc_bipush                 = 16;    //将单字节的常量值(-128~127)推送至栈顶
    private static final int opc_sipush                 = 17;    //将一个短整型常量值(-32768~32767)推送至栈顶
    private static final int opc_ldc                    = 18;    //将int, float或String型常量值从常量池中推送至栈顶
    private static final int opc_ldc_w                  = 19;    //将int, float或String型常量值从常量池中推送至栈顶（宽索引）
//  private static final int opc_ldc2_w                 = 20;    //将long或double型常量值从常量池中推送至栈顶（宽索引）
    private static final int opc_iload                  = 21;    //将指定的int型本地变量推送至栈顶
    private static final int opc_lload                  = 22;    //将指定的long型本地变量推送至栈顶
    private static final int opc_fload                  = 23;    //将指定的float型本地变量推送至栈顶
    private static final int opc_dload                  = 24;    //将指定的double型本地变量推送至栈顶
    private static final int opc_aload                  = 25;    //将指定的引用类型本地变量推送至栈顶
    private static final int opc_iload_0                = 26;    //将第一个int型本地变量推送至栈顶
//  private static final int opc_iload_1                = 27;    //将第二个int型本地变量推送至栈顶
//  private static final int opc_iload_2                = 28;    //将第三个int型本地变量推送至栈顶
//  private static final int opc_iload_3                = 29;    //将第四个int型本地变量推送至栈顶
    private static final int opc_lload_0                = 30;    //将第一个long型本地变量推送至栈顶
//  private static final int opc_lload_1                = 31;    //将第二个long型本地变量推送至栈顶
//  private static final int opc_lload_2                = 32;    //将第三个long型本地变量推送至栈顶
//  private static final int opc_lload_3                = 33;    //将第四个long型本地变量推送至栈顶
    private static final int opc_fload_0                = 34;    //将第一个float型本地变量推送至栈顶
//  private static final int opc_fload_1                = 35;    //将第二个float型本地变量推送至栈顶
//  private static final int opc_fload_2                = 36;    //将第三个float型本地变量推送至栈顶
//  private static final int opc_fload_3                = 37;    //将第四个float型本地变量推送至栈顶
    private static final int opc_dload_0                = 38;    //将第一个double型本地变量推送至栈顶
//  private static final int opc_dload_1                = 39;    //将第二个double型本地变量推送至栈顶
//  private static final int opc_dload_2                = 40;    //将第三个double型本地变量推送至栈顶
//  private static final int opc_dload_3                = 41;    //将第四个double型本地变量推送至栈顶
    private static final int opc_aload_0                = 42;    //将第一个引用类型本地变量推送至栈顶
//  private static final int opc_aload_1                = 43;    //将第二个引用类型本地变量推送至栈顶
//  private static final int opc_aload_2                = 44;    //将第三个引用类型本地变量推送至栈顶
//  private static final int opc_aload_3                = 45;    //将第四个引用类型本地变量推送至栈顶
//  private static final int opc_iaload                 = 46;    //将int型数组指定索引的值推送至栈顶
//  private static final int opc_laload                 = 47;    //将long型数组指定索引的值推送至栈顶
//  private static final int opc_faload                 = 48;    //将float型数组指定索引的值推送至栈顶
//  private static final int opc_daload                 = 49;    //将double型数组指定索引的值推送至栈顶
//  private static final int opc_aaload                 = 50;    //将引用型数组指定索引的值推送至栈顶
//  private static final int opc_baload                 = 51;    //将boolean或byte型数组指定索引的值推送至栈顶
//  private static final int opc_caload                 = 52;    //将char型数组指定索引的值推送至栈顶
//  private static final int opc_saload                 = 53;    //将short型数组指定索引的值推送至栈顶
//  private static final int opc_istore                 = 54;    //将栈顶int型数值存入指定本地变量
//  private static final int opc_lstore                 = 55;    //将栈顶long型数值存入指定本地变量
//  private static final int opc_fstore                 = 56;    //将栈顶float型数值存入指定本地变量
//  private static final int opc_dstore                 = 57;    //将栈顶double型数值存入指定本地变量
    private static final int opc_astore                 = 58;    //将栈顶引用型数值存入指定本地变量
//  private static final int opc_istore_0               = 59;    //将栈顶int型数值存入第一个本地变量
//  private static final int opc_istore_1               = 60;    //将栈顶int型数值存入第二个本地变量
//  private static final int opc_istore_2               = 61;    //将栈顶int型数值存入第三个本地变量
//  private static final int opc_istore_3               = 62;    //将栈顶int型数值存入第四个本地变量
//  private static final int opc_lstore_0               = 63;    //将栈顶long型数值存入第一个本地变量
//  private static final int opc_lstore_1               = 64;    //将栈顶long型数值存入第二个本地变量
//  private static final int opc_lstore_2               = 65;    //将栈顶long型数值存入第三个本地变量
//  private static final int opc_lstore_3               = 66;    //将栈顶long型数值存入第四个本地变量
//  private static final int opc_fstore_0               = 67;    //将栈顶float型数值存入第一个本地变量
//  private static final int opc_fstore_1               = 68;    //将栈顶float型数值存入第二个本地变量
//  private static final int opc_fstore_2               = 69;    //将栈顶float型数值存入第三个本地变量
//  private static final int opc_fstore_3               = 70;    //将栈顶float型数值存入第四个本地变量
//  private static final int opc_dstore_0               = 71;    //将栈顶double型数值存入第一个本地变量
//  private static final int opc_dstore_1               = 72;    //将栈顶double型数值存入第二个本地变量
//  private static final int opc_dstore_2               = 73;    //将栈顶double型数值存入第三个本地变量
//  private static final int opc_dstore_3               = 74;    //将栈顶double型数值存入第四个本地变量
    private static final int opc_astore_0               = 75;    //将栈顶引用型数值存入第一个本地变量
//  private static final int opc_astore_1               = 76;    //将栈顶引用型数值存入第二个本地变量
//  private static final int opc_astore_2               = 77;    //将栈顶引用型数值存入第三个本地变量
//  private static final int opc_astore_3               = 78;    //将栈顶引用型数值存入第四个本地变量
//  private static final int opc_iastore                = 79;    //将栈顶int型数值存入指定数组的指定索引位置
//  private static final int opc_lastore                = 80;    //将栈顶long型数值存入指定数组的指定索引位置
//  private static final int opc_fastore                = 81;    //将栈顶float型数值存入指定数组的指定索引位置
//  private static final int opc_dastore                = 82;    //将栈顶double型数值存入指定数组的指定索引位置
    private static final int opc_aastore                = 83;    //将栈顶引用型数值存入指定数组的指定索引位置
//  private static final int opc_bastore                = 84;    //将栈顶boolean或byte型数值存入指定数组的指定索引位置
//  private static final int opc_castore                = 85;    //将栈顶char型数值存入指定数组的指定索引位置
//  private static final int opc_sastore                = 86;    //将栈顶short型数值存入指定数组的指定索引位置
    private static final int opc_pop                    = 87;    //将栈顶数值弹出 (数值不能是long或double类型的)
//  private static final int opc_pop2                   = 88;    //将栈顶的一个(long或double类型的)或两个数值弹出(其它)
    private static final int opc_dup                    = 89;    //复制栈顶数值并将复制值压入栈顶
//  private static final int opc_dup_x1                 = 90;    //复制栈顶数值并将两个复制值压入栈顶
//  private static final int opc_dup_x2                 = 91;    //复制栈顶数值并将三个（或两个）复制值压入栈顶
//  private static final int opc_dup2                   = 92;    //复制栈顶一个（long或double类型的)或两个（其它）数值并将复制值压入栈顶
//  private static final int opc_dup2_x1                = 93;    //待补充
//  private static final int opc_dup2_x2                = 94;    //待补充
//  private static final int opc_swap                   = 95;    //将栈最顶端的两个数值互换(数值不能是long或double类型的)
//  private static final int opc_iadd                   = 96;    //将栈顶两int型数值相加并将结果压入栈顶
//  private static final int opc_ladd                   = 97;    //将栈顶两long型数值相加并将结果压入栈顶
//  private static final int opc_fadd                   = 98;    //将栈顶两float型数值相加并将结果压入栈顶
//  private static final int opc_dadd                   = 99;    //将栈顶两double型数值相加并将结果压入栈顶
//  private static final int opc_isub                   = 100;   //将栈顶两int型数值相减并将结果压入栈顶
//  private static final int opc_lsub                   = 101;   //将栈顶两long型数值相减并将结果压入栈顶
//  private static final int opc_fsub                   = 102;   //将栈顶两float型数值相减并将结果压入栈顶
//  private static final int opc_dsub                   = 103;   //将栈顶两double型数值相减并将结果压入栈顶
//  private static final int opc_imul                   = 104;   //将栈顶两int型数值相乘并将结果压入栈顶
//  private static final int opc_lmul                   = 105;   //将栈顶两long型数值相乘并将结果压入栈顶
//  private static final int opc_fmul                   = 106;   //将栈顶两float型数值相乘并将结果压入栈顶
//  private static final int opc_dmul                   = 107;   //将栈顶两double型数值相乘并将结果压入栈顶
//  private static final int opc_idiv                   = 108;   //将栈顶两int型数值相除并将结果压入栈顶
//  private static final int opc_ldiv                   = 109;   //将栈顶两long型数值相除并将结果压入栈顶
//  private static final int opc_fdiv                   = 110;   //将栈顶两float型数值相除并将结果压入栈顶
//  private static final int opc_ddiv                   = 111;   //将栈顶两double型数值相除并将结果压入栈顶
//  private static final int opc_irem                   = 112;   //将栈顶两int型数值作取模运算并将结果压入栈顶
//  private static final int opc_lrem                   = 113;   //将栈顶两long型数值作取模运算并将结果压入栈顶
//  private static final int opc_frem                   = 114;   //将栈顶两float型数值作取模运算并将结果压入栈顶
//  private static final int opc_drem                   = 115;   //将栈顶两double型数值作取模运算并将结果压入栈顶
//  private static final int opc_ineg                   = 116;   //将栈顶int型数值取负并将结果压入栈顶
//  private static final int opc_lneg                   = 117;   //将栈顶long型数值取负并将结果压入栈顶
//  private static final int opc_fneg                   = 118;   //将栈顶float型数值取负并将结果压入栈顶
//  private static final int opc_dneg                   = 119;   //将栈顶double型数值取负并将结果压入栈顶
//  private static final int opc_ishl                   = 120;   //将int型数值左移位指定位数并将结果压入栈顶
//  private static final int opc_lshl                   = 121;   //将long型数值左移位指定位数并将结果压入栈顶
//  private static final int opc_ishr                   = 122;   //将int型数值右（符号）移位指定位数并将结果压入栈顶
//  private static final int opc_lshr                   = 123;   //将long型数值右（符号）移位指定位数并将结果压入栈顶
//  private static final int opc_iushr                  = 124;   //将int型数值右（无符号）移位指定位数并将结果压入栈顶
//  private static final int opc_lushr                  = 125;   //将long型数值右（无符号）移位指定位数并将结果压入栈顶
//  private static final int opc_iand                   = 126;   //将栈顶两int型数值作“按位与”并将结果压入栈顶
//  private static final int opc_land                   = 127;   //将栈顶两long型数值作“按位与”并将结果压入栈顶
//  private static final int opc_ior                    = 128;   //将栈顶两int型数值作“按位或”并将结果压入栈顶
//  private static final int opc_lor                    = 129;   //将栈顶两long型数值作“按位或”并将结果压入栈顶
//  private static final int opc_ixor                   = 130;   //将栈顶两int型数值作“按位异或”并将结果压入栈顶
//  private static final int opc_lxor                   = 131;   //将栈顶两long型数值作“按位异或”并将结果压入栈顶
//  private static final int opc_iinc                   = 132;   //将指定int型变量增加指定值（i++, i--, i+=2）
//  private static final int opc_i2l                    = 133;   //将栈顶int型数值强制转换成long型数值并将结果压入栈顶
//  private static final int opc_i2f                    = 134;   //将栈顶int型数值强制转换成float型数值并将结果压入栈顶
//  private static final int opc_i2d                    = 135;   //将栈顶int型数值强制转换成double型数值并将结果压入栈顶
//  private static final int opc_l2i                    = 136;   //将栈顶long型数值强制转换成int型数值并将结果压入栈顶
//  private static final int opc_l2f                    = 137;   //将栈顶long型数值强制转换成float型数值并将结果压入栈顶
//  private static final int opc_l2d                    = 138;   //将栈顶long型数值强制转换成double型数值并将结果压入栈顶
//  private static final int opc_f2i                    = 139;   //将栈顶float型数值强制转换成int型数值并将结果压入栈顶
//  private static final int opc_f2l                    = 140;   //将栈顶float型数值强制转换成long型数值并将结果压入栈顶
//  private static final int opc_f2d                    = 141;   //将栈顶float型数值强制转换成double型数值并将结果压入栈顶
//  private static final int opc_d2i                    = 142;   //将栈顶double型数值强制转换成int型数值并将结果压入栈顶
//  private static final int opc_d2l                    = 143;   //将栈顶double型数值强制转换成long型数值并将结果压入栈顶
//  private static final int opc_d2f                    = 144;   //将栈顶double型数值强制转换成float型数值并将结果压入栈顶
//  private static final int opc_i2b                    = 145;   //将栈顶int型数值强制转换成byte型数值并将结果压入栈顶
//  private static final int opc_i2c                    = 146;   //将栈顶int型数值强制转换成char型数值并将结果压入栈顶
//  private static final int opc_i2s                    = 147;   //将栈顶int型数值强制转换成short型数值并将结果压入栈顶
//  private static final int opc_lcmp                   = 148;   //比较栈顶两long型数值大小，并将结果（1，0，-1）压入栈顶
//  private static final int opc_fcmpl                  = 149;   //比较栈顶两float型数值大小，并将结果（1，0，-1）压入栈顶；当其中一个数值为NaN时，将-1压入栈顶
//  private static final int opc_fcmpg                  = 150;   //比较栈顶两float型数值大小，并将结果（1，0，-1）压入栈顶；当其中一个数值为NaN时，将1压入栈顶
//  private static final int opc_dcmpl                  = 151;   //比较栈顶两double型数值大小，并将结果（1，0，-1）压入栈顶；当其中一个数值为NaN时，将-1压入栈顶
//  private static final int opc_dcmpg                  = 152;   //比较栈顶两double型数值大小，并将结果（1，0，-1）压入栈顶；当其中一个数值为NaN时，将1压入栈顶
//  private static final int opc_ifeq                   = 153;   //当栈顶int型数值等于0时跳转
//  private static final int opc_ifne                   = 154;   //当栈顶int型数值不等于0时跳转
//  private static final int opc_iflt                   = 155;   //当栈顶int型数值小于0时跳转
//  private static final int opc_ifge                   = 156;   //当栈顶int型数值大于等于0时跳转
//  private static final int opc_ifgt                   = 157;   //当栈顶int型数值大于0时跳转
//  private static final int opc_ifle                   = 158;   //当栈顶int型数值小于等于0时跳转
//  private static final int opc_if_icmpeq              = 159;   //比较栈顶两int型数值大小，当结果等于0时跳转
//  private static final int opc_if_icmpne              = 160;   //比较栈顶两int型数值大小，当结果不等于0时跳转
//  private static final int opc_if_icmplt              = 161;   //比较栈顶两int型数值大小，当结果小于0时跳转
//  private static final int opc_if_icmpge              = 162;   //比较栈顶两int型数值大小，当结果大于等于0时跳转
//  private static final int opc_if_icmpgt              = 163;   //比较栈顶两int型数值大小，当结果大于0时跳转
//  private static final int opc_if_icmple              = 164;   //比较栈顶两int型数值大小，当结果小于等于0时跳转
//  private static final int opc_if_acmpeq              = 165;   //比较栈顶两引用型数值，当结果相等时跳转
//  private static final int opc_if_acmpne              = 166;   //比较栈顶两引用型数值，当结果不相等时跳转
//  private static final int opc_goto                   = 167;   //无条件跳转
//  private static final int opc_jsr                    = 168;   //跳转至指定16位offset位置，并将jsr下一条指令地址压入栈顶
//  private static final int opc_ret                    = 169;   //返回至本地变量指定的index的指令位置（一般与jsr, jsr_w联合使用）
//  private static final int opc_tableswitch            = 170;   //用于switch条件跳转，case值连续（可变长度指令）
//  private static final int opc_lookupswitch           = 171;   //用于switch条件跳转，case值不连续（可变长度指令）
    private static final int opc_ireturn                = 172;   //从当前方法返回int
    private static final int opc_lreturn                = 173;   //从当前方法返回long
    private static final int opc_freturn                = 174;   //从当前方法返回float
    private static final int opc_dreturn                = 175;   //从当前方法返回double
    private static final int opc_areturn                = 176;   //从当前方法返回对象引用
    private static final int opc_return                 = 177;   //从当前方法返回void
    private static final int opc_getstatic              = 178;   //获取指定类的静态域，并将其值压入栈顶
    private static final int opc_putstatic              = 179;   //为指定的类的静态域赋值
    private static final int opc_getfield               = 180;   //获取指定类的实例域，并将其值压入栈顶
//  private static final int opc_putfield               = 181;   //为指定的类的实例域赋值
    private static final int opc_invokevirtual          = 182;   //调用实例方法
    private static final int opc_invokespecial          = 183;   //调用超类构造方法，实例初始化方法，私有方法
    private static final int opc_invokestatic           = 184;   //调用静态方法
    private static final int opc_invokeinterface        = 185;   //调用接口方法
    private static final int opc_new                    = 187;   //创建一个对象，并将其引用值压入栈顶
//  private static final int opc_newarray               = 188;   //创建一个指定原始类型（如int, float, char…）的数组，并将其引用值压入栈顶
    private static final int opc_anewarray              = 189;   //创建一个引用型（如类，接口，数组）的数组，并将其引用值压入栈顶
//  private static final int opc_arraylength            = 190;   //获得数组的长度值并压入栈顶
    private static final int opc_athrow                 = 191;   //将栈顶的异常抛出
    private static final int opc_checkcast              = 192;   //检验类型转换，检验未通过将抛出ClassCastException
//  private static final int opc_instanceof             = 193;   //检验对象是否是指定的类的实例，如果是将1压入栈顶，否则将0压入栈顶
//  private static final int opc_monitorenter           = 194;   //获得对象的锁，用于同步方法或同步块
//  private static final int opc_monitorexit            = 195;   //释放对象的锁，用于同步方法或同步块
    private static final int opc_wide                   = 196;   //待补充
//  private static final int opc_multianewarray         = 197;   //创建指定类型和指定维度的多维数组（执行该指令时，操作栈中必须包含各维度的长度值），并将其引用值压入栈顶
//  private static final int opc_ifnull                 = 198;   //为null时跳转
//  private static final int opc_ifnonnull              = 199;   //不为null时跳转
//  private static final int opc_goto_w                 = 200;   //无条件跳转（宽索引）
//  private static final int opc_jsr_w                  = 201;   //跳转至指定32位offset位置，并将jsr_w下一条指令地址压入栈顶

    
    //生成的代理类的父类名称
    private final static String superclassName = "java/lang/reflect/Proxy";

    //代理类实例的属性名, 用来存放InvocationHandler
    private final static String handlerFieldName = "h";

    //用于保存生成的类文件的调试标志
    private final static boolean saveGeneratedFiles =
        java.security.AccessController.doPrivileged(
            new GetBooleanAction("sun.misc.ProxyGenerator.saveGeneratedFiles")).booleanValue();

    //根据给定的代理类全限定名和接口集合来生成代理类
    public static byte[] generateProxyClass(final String name, Class[] interfaces) {
        ProxyGenerator gen = new ProxyGenerator(name, interfaces);
        //生成代理类二进制数组的核心方法
        final byte[] classFile = gen.generateClassFile();

        if (saveGeneratedFiles) {
            java.security.AccessController.doPrivileged(
            new java.security.PrivilegedAction<Void>() {
                public Void run() {
                    try {
                    	//取得代理类全限定名最后一个.的位置
                        int i = name.lastIndexOf('.');
                        Path path;
                        if (i > 0) {
                        	//例子：com.sun.proxy.$Proxy0
                        	//这里将会截取字符串com.sun.proxy, 并将.替换成/, 最后得到com/sun/proxy
                            Path dir = Paths.get(name.substring(0, i).replace('.', File.separatorChar));
                            //根据Path生成目录
                            Files.createDirectories(dir);
                            //path转换成$Proxy0.class
                            path = dir.resolve(name.substring(i+1, name.length()) + ".class");
                        } else {
                            path = Paths.get(name + ".class");
                        }
                        //这里将二进制数组写入$Proxy0.class
                        Files.write(path, classFile);
                        return null;
                    } catch (IOException e) {
                        throw new InternalError(
                            "I/O exception saving generated file: " + e);
                    }
                }
            });
        }

        return classFile;
    }

    
    private static Method hashCodeMethod;      //hashCode方法
    private static Method equalsMethod;        //equal方法
    private static Method toStringMethod;      //toString方法
    
    //类加载时设置
    static {
        try {
            hashCodeMethod = Object.class.getMethod("hashCode");
            equalsMethod = Object.class.getMethod("equals", new Class[] { Object.class });
            toStringMethod = Object.class.getMethod("toString");
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodError(e.getMessage());
        }
    }

    //代理类的名称
    private String className;

    //代理类的接口
    private Class[] interfaces;

    //生成class文件的常量池
    private ConstantPool cp = new ConstantPool();

    //生成class文件的字段信息集合
    private List<FieldInfo> fields = new ArrayList<FieldInfo>();

    //生成class文件的方法信息集合
    private List<MethodInfo> methods = new ArrayList<MethodInfo>();

    //代理方法签名和ProxyMethod集合的映射
    private Map<String, List<ProxyMethod>> proxyMethods = new HashMap<String,List<ProxyMethod>>();

    //proxyMethods映射表中ProxyMethod对象的数目
    private int proxyMethodCount = 0;

    //构造器, 需要指定具体的名字和接口集合
    private ProxyGenerator(String className, Class[] interfaces) {
        this.className = className;
        this.interfaces = interfaces;
    }

    //这是生成class字节码文件的核心方法
	private byte[] generateClassFile() {
		//第一步, 将所有的方法组装成ProxyMethod对象
		//首先为代理类生成toString, hashCode, equals等代理方法
	    addProxyMethod(hashCodeMethod, Object.class);
	    addProxyMethod(equalsMethod, Object.class);
	    addProxyMethod(toStringMethod, Object.class);
	    //遍历每一个接口的每一个方法, 并且为其生成ProxyMethod对象
	    for (int i = 0; i < interfaces.length; i++) {
	        Method[] methods = interfaces[i].getMethods();
	        for (int j = 0; j < methods.length; j++) {
	            addProxyMethod(methods[j], interfaces[i]);
	        }
	    }
	    //对于具有相同签名的代理方法, 检验方法的返回值是否兼容
	    for (List<ProxyMethod> sigmethods : proxyMethods.values()) {
	        checkReturnTypes(sigmethods);
	    }
	    
	    //第二步, 组装要生成的class文件的所有的字段信息和方法信息
	    try {
	    	//添加构造器方法
	        methods.add(generateConstructor());
	        //遍历缓存中的代理方法
	        for (List<ProxyMethod> sigmethods : proxyMethods.values()) {
	            for (ProxyMethod pm : sigmethods) {
	            	//添加代理类的静态字段, 例如:private static Method m1;
	                fields.add(new FieldInfo(pm.methodFieldName,
	                		"Ljava/lang/reflect/Method;", ACC_PRIVATE | ACC_STATIC));
	                //添加代理类的代理方法
	                methods.add(pm.generateMethod());
	            }
	        }
	        //添加代理类的静态字段初始化方法
	        methods.add(generateStaticInitializer());
	    } catch (IOException e) {
	        throw new InternalError("unexpected I/O Exception");
	    }
	    
	    //验证方法和字段集合不能大于65535
	    if (methods.size() > 65535) {
	        throw new IllegalArgumentException("method limit exceeded");
	    }
	    if (fields.size() > 65535) {
	        throw new IllegalArgumentException("field limit exceeded");
	    }
	
	    //第三步, 写入最终的class文件
	    //验证常量池中存在代理类的全限定名
	    cp.getClass(dotToSlash(className));
	    //验证常量池中存在代理类父类的全限定名, 父类名为:"java/lang/reflect/Proxy"
	    cp.getClass(superclassName);
	    //验证常量池存在代理类接口的全限定名
	    for (int i = 0; i < interfaces.length; i++) {
	        cp.getClass(dotToSlash(interfaces[i].getName()));
	    }
	    //接下来要开始写入文件了,设置常量池只读
	    cp.setReadOnly();
	    
	    ByteArrayOutputStream bout = new ByteArrayOutputStream();
	    DataOutputStream dout = new DataOutputStream(bout);
	    try {
	    	//1.写入魔数
	        dout.writeInt(0xCAFEBABE);
	        //2.写入次版本号
	        dout.writeShort(CLASSFILE_MINOR_VERSION);
	        //3.写入主版本号
	        dout.writeShort(CLASSFILE_MAJOR_VERSION);
	        //4.写入常量池
	        cp.write(dout);
	        //5.写入访问修饰符
	        dout.writeShort(ACC_PUBLIC | ACC_FINAL | ACC_SUPER);
	        //6.写入类索引
	        dout.writeShort(cp.getClass(dotToSlash(className)));
	        //7.写入父类索引, 生成的代理类都继承自Proxy
	        dout.writeShort(cp.getClass(superclassName));
	        //8.写入接口计数值
	        dout.writeShort(interfaces.length);
	        //9.写入接口集合
	        for (int i = 0; i < interfaces.length; i++) {
	            dout.writeShort(cp.getClass(dotToSlash(interfaces[i].getName())));
	        }
	        //10.写入字段计数值
	        dout.writeShort(fields.size());
	        //11.写入字段集合 
	        for (FieldInfo f : fields) {
	            f.write(dout);
	        }
	        //12.写入方法计数值
	        dout.writeShort(methods.size());
	        //13.写入方法集合
	        for (MethodInfo m : methods) {
	            m.write(dout);
	        }
	        //14.写入属性计数值, 代理类class文件没有属性所以为0
	        dout.writeShort(0);
	    } catch (IOException e) {
	        throw new InternalError("unexpected I/O Exception");
	    }
	    //转换成二进制数组输出
	    return bout.toByteArray();
	}

    
    //添加代理方法, 新建ProxyMethod对象或者为重复的方法增加一个旧的
    //fromClass表示代理接口
    private void addProxyMethod(Method m, Class fromClass) {
    	//获取方法简单名称
        String name = m.getName();
        //获取方法参数集合
        Class[] parameterTypes = m.getParameterTypes();
        //获取方法返回类型
        Class returnType = m.getReturnType();
        //获取方法声明的异常集合
        Class[] exceptionTypes = m.getExceptionTypes();
        //生成方法特征签名
        String sig = name + getParameterDescriptors(parameterTypes);
        //根据方法特征签名从缓存获取代理方法集合
        List<ProxyMethod> sigmethods = proxyMethods.get(sig);
        
        if (sigmethods != null) {
            for (ProxyMethod pm : sigmethods) {
            	//还要判断返回值是否对应的上
                if (returnType == pm.returnType) {
                    List<Class<?>> legalExceptions = new ArrayList<Class<?>>();
                    //收集兼容的异常类型
                    collectCompatibleTypes(exceptionTypes, pm.exceptionTypes, legalExceptions);
                    collectCompatibleTypes(pm.exceptionTypes, exceptionTypes, legalExceptions);
                    
                    //新建代理方法的异常类型数组
                    pm.exceptionTypes = new Class[legalExceptions.size()];
                    //将legalExceptions合法的异常转化成数组赋给pm.exceptionTypes
                    pm.exceptionTypes = legalExceptions.toArray(pm.exceptionTypes);
                    
                    return;
                }
            }
        } else {
            sigmethods = new ArrayList<ProxyMethod>(3);
            //将方法特征签名和代理方法列表关联并放入缓存
            proxyMethods.put(sig, sigmethods);
        }
        //构造ProxyMethod实例并将其添加到代理方法列表中
        sigmethods.add(new ProxyMethod(name, parameterTypes, returnType, exceptionTypes, fromClass));
    }

    /**
     * For a given set of proxy methods with the same signature, check
     * that their return types are compatible according to the Proxy
     * specification.
     *
     * Specifically, if there is more than one such method, then all
     * of the return types must be reference types, and there must be
     * one return type that is assignable to each of the rest of them.
     */
    private static void checkReturnTypes(List<ProxyMethod> methods) {
        /*
         * If there is only one method with a given signature, there
         * cannot be a conflict.  This is the only case in which a
         * primitive (or void) return type is allowed.
         */
        if (methods.size() < 2) {
            return;
        }

        /*
         * List of return types that are not yet known to be
         * assignable from ("covered" by) any of the others.
         */
        LinkedList<Class<?>> uncoveredReturnTypes = new LinkedList<Class<?>>();

    nextNewReturnType:
        for (ProxyMethod pm : methods) {
            Class<?> newReturnType = pm.returnType;
            if (newReturnType.isPrimitive()) {
                throw new IllegalArgumentException("methods with same signature " + getFriendlyMethodSignature(pm.methodName, pm.parameterTypes) +
                    " but incompatible return types: " + newReturnType.getName() + " and others");
            }
            boolean added = false;

            /*
             * Compare the new return type to the existing uncovered
             * return types.
             */
            ListIterator<Class<?>> liter = uncoveredReturnTypes.listIterator();
            while (liter.hasNext()) {
                Class<?> uncoveredReturnType = liter.next();

                /*
                 * If an existing uncovered return type is assignable
                 * to this new one, then we can forget the new one.
                 */
                if (newReturnType.isAssignableFrom(uncoveredReturnType)) {
                    assert !added;
                    continue nextNewReturnType;
                }

                /*
                 * If the new return type is assignable to an existing
                 * uncovered one, then should replace the existing one
                 * with the new one (or just forget the existing one,
                 * if the new one has already be put in the list).
                 */
                if (uncoveredReturnType.isAssignableFrom(newReturnType)) {
                    // (we can assume that each return type is unique)
                    if (!added) {
                        liter.set(newReturnType);
                        added = true;
                    } else {
                        liter.remove();
                    }
                }
            }

            /*
             * If we got through the list of existing uncovered return
             * types without an assignability relationship, then add
             * the new return type to the list of uncovered ones.
             */
            if (!added) {
                uncoveredReturnTypes.add(newReturnType);
            }
        }

        /*
         * We shouldn't end up with more than one return type that is
         * not assignable from any of the others.
         */
        if (uncoveredReturnTypes.size() > 1) {
            ProxyMethod pm = methods.get(0);
            throw new IllegalArgumentException(
                "methods with same signature " +
                getFriendlyMethodSignature(pm.methodName, pm.parameterTypes) +
                " but incompatible return types: " + uncoveredReturnTypes);
        }
    }

    //代表生成class文件中字段信息
    private class FieldInfo {
        public int accessFlags;     //字段访问标志
        public String name;         //字段名称
        public String descriptor;   //字段描述符

        public FieldInfo(String name, String descriptor, int accessFlags) {
            this.name = name;
            this.descriptor = descriptor;
            this.accessFlags = accessFlags;
            //确定常量池存在字段名称和字段描述符索引
            cp.getUtf8(name);
            cp.getUtf8(descriptor);
        }

        public void write(DataOutputStream out) throws IOException {
            //u2 字段访问标志
            out.writeShort(accessFlags);
            //u2 字段名称索引
            out.writeShort(cp.getUtf8(name));
            //u2 字段描述符索引
            out.writeShort(cp.getUtf8(descriptor));
            //u2 属性表容量
            out.writeShort(0);
        }
    }

    //代表方法信息中的异常表
    private static class ExceptionTableEntry {
        public short startPc;
        public short endPc;
        public short handlerPc;
        public short catchType;

        public ExceptionTableEntry(short startPc, short endPc,
                                   short handlerPc, short catchType)
        {
            this.startPc = startPc;
            this.endPc = endPc;
            this.handlerPc = handlerPc;
            this.catchType = catchType;
        }
    };

    //代表生成class文件中的方法信息
    private class MethodInfo {
        public int accessFlags;   //访问标志
        public String name;       //方法名称
        public String descriptor; //方法特征签名
        public short maxStack;    //操作数栈最大深度
        public short maxLocals;   //本地变量表最大深度
        public ByteArrayOutputStream code = new ByteArrayOutputStream();
        public List<ExceptionTableEntry> exceptionTable = new ArrayList<ExceptionTableEntry>();
        public short[] declaredExceptions;

        public MethodInfo(String name, String descriptor, int accessFlags) {
            this.name = name;
            this.descriptor = descriptor;
            this.accessFlags = accessFlags;

            //在开始写入最终的class文件前先确认以下项目的常量池索引仍然保留着
            //在构造MethodInfo时就向常量池写入这些值
            cp.getUtf8(name);
            cp.getUtf8(descriptor);
            cp.getUtf8("Code");
            cp.getUtf8("Exceptions");
        }

        public void write(DataOutputStream out) throws IOException {
            
            //u2 访问标志
            out.writeShort(accessFlags);
            //u2 方法名称
            out.writeShort(cp.getUtf8(name));
            //u2 方法描述信息
            out.writeShort(cp.getUtf8(descriptor));
            //u2 属性表的数量
            out.writeShort(2);

            //开始写入Code属性
            
            //u2 属性表名称索引
            out.writeShort(cp.getUtf8("Code"));
            //u4 属性表长度
            out.writeInt(12 + code.size() + 8 * exceptionTable.size());
            //u2 操作数栈最大值
            out.writeShort(maxStack);
            //u2 本地变量表 最大值              
            out.writeShort(maxLocals);
            //u2 操作指令集的数量
            out.writeInt(code.size());
            //u1 code[code_length];
            //写入操作指令集
            code.writeTo(out);
            
            //u2 异常表长度
            out.writeShort(exceptionTable.size());
            for (ExceptionTableEntry e : exceptionTable) {
                //u2 开始位置
                out.writeShort(e.startPc);
                //u2 结束位置
                out.writeShort(e.endPc);
                //u2 处理位置 
                out.writeShort(e.handlerPc);
                //u2 捕获的异常类型
                out.writeShort(e.catchType);
            }
            //u2 属性表个数
            out.writeShort(0);

            //写入方法的异常属性

            //u2 异常属性表的名称索引
            out.writeShort(cp.getUtf8("Exceptions"));
            //u4 属性表长度
            out.writeInt(2 + 2 * declaredExceptions.length);
            //u2 声明的异常的数量 
            out.writeShort(declaredExceptions.length);
            //u2 exception_index_table[number_of_exceptions];
            for (int i = 0; i < declaredExceptions.length; i++) {
            	//遍历声明的异常, 开始写入异常
                out.writeShort(declaredExceptions[i]);
            }
        }

    }

    //代表了代理类中的代理方法
    private class ProxyMethod {

        public String methodName;       //方法名称
        public Class[] parameterTypes;  //参数类型集合
        public Class returnType;        //返回参数
        public Class[] exceptionTypes;  //方法声明的异常类型集合
        public Class fromClass;         //代理方法来自的类
        public String methodFieldName;  //方法字段名

        private ProxyMethod(String methodName, Class[] parameterTypes,
                            Class returnType, Class[] exceptionTypes,
                            Class fromClass)
        {
            this.methodName = methodName;
            this.parameterTypes = parameterTypes;
            this.returnType = returnType;
            this.exceptionTypes = exceptionTypes;
            this.fromClass = fromClass;
            this.methodFieldName = "m" + proxyMethodCount++;
        }

        //这个方法将会返回一个MethodInfo对象, 包含了指令集和异常表对象
        private MethodInfo generateMethod() throws IOException {
        	//根据参数类型集合和返回类型来获取方法描述信息
            String desc = getMethodDescriptor(parameterTypes, returnType);
            //根据方法名称, 方法描述信息, 方法访问标志构造MethodInfo对象
            MethodInfo minfo = new MethodInfo(methodName, desc, ACC_PUBLIC | ACC_FINAL);

            //参数数组
            int[] parameterSlot = new int[parameterTypes.length];
            int nextSlot = 1;
            for (int i = 0; i < parameterSlot.length; i++) {
            	//首先给参数数组赋值为1, 方法第一个参数传入this
                parameterSlot[i] = nextSlot;
                //之后再加上参数类型对应的slot, long和double为2其他为1
                nextSlot += getWordsPerType(parameterTypes[i]);
            }
            //这时本地变量表只存放方法参数
            int localSlot0 = nextSlot;
            short pc, tryBegin = 0, tryEnd;

            //包装了操作指令集的字节流
            DataOutputStream out = new DataOutputStream(minfo.code);
            
            //下面写到out中的都是往minfo的操作指令集写
            
            //将0加载到操作数栈顶
            code_aload(0, out);
            
            //获取指定类的实例域，并将其值压入栈顶
            out.writeByte(opc_getfield);
            //获取Proxy对象的实例域h
            out.writeShort(cp.getFieldRef(superclassName, handlerFieldName, "Ljava/lang/reflect/InvocationHandler;"));

            //将0加载到栈顶
            code_aload(0, out);

            //获取指定类的静态域，并将其值压入栈顶
            out.writeByte(opc_getstatic);
            //获取代理类的静态域m1
            out.writeShort(cp.getFieldRef(dotToSlash(className), methodFieldName, "Ljava/lang/reflect/Method;"));

            if (parameterTypes.length > 0) {
            	//将代理方法参数类型集合长度的值推到栈顶
                code_ipush(parameterTypes.length, out);
                
                //新建一个Object数组, 并将其引用值压入栈顶, 例如：Object[] o = new Object[parameterTypes.length];
                out.writeByte(opc_anewarray);
                out.writeShort(cp.getClass("java/lang/Object"));

                for (int i = 0; i < parameterTypes.length; i++) {
                	//复制栈顶数值并将复制值压入栈顶
                    out.writeByte(opc_dup);
                    //将i的值推送到栈顶
                    code_ipush(i, out);
                    //如果参数类型是基本类型则返回它的包装器类的引用到栈顶
                    codeWrapArgument(parameterTypes[i], parameterSlot[i], out);
                    //将栈顶引用型数值存入指定数组的指定索引位置
                    //例如：o[i] = parameterTypes[i];
                    out.writeByte(opc_aastore);
                }
            } else {
            	//否则,该方法没有参数,将null推送至栈顶
                out.writeByte(opc_aconst_null);
            }

            //调用接口方法
            out.writeByte(opc_invokeinterface);
            //调用invoke方法
            out.writeShort(cp.getInterfaceMethodRef("java/lang/reflect/InvocationHandler", "invoke",
                "(Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;"));
            
            out.writeByte(4);
            out.writeByte(0);
            
            //如果返回void类型
            if (returnType == void.class) {
            	//将栈顶数值弹出(数值不能是long或double类型的)
                out.writeByte(opc_pop);
                //从当前方法返回void
                out.writeByte(opc_return);
            } else {
            	//先将返回值拆箱后再返回
                codeUnwrapReturnValue(returnType, out);
            }

            //try块包含了整个代码块
            tryEnd = pc = (short) minfo.code.size();

            List<Class<?>> catchList = computeUniqueCatchList(exceptionTypes);
            if (catchList.size() > 0) {

                for (Class<?> ex : catchList) {
                    minfo.exceptionTable.add(new ExceptionTableEntry(
                        tryBegin, tryEnd, pc,
                        cp.getClass(dotToSlash(ex.getName()))));
                }
                
                //将栈顶的异常抛出
                out.writeByte(opc_athrow);
                //将程序计数器更新到方法最后
                pc = (short) minfo.code.size();

                minfo.exceptionTable.add(new ExceptionTableEntry(
                    tryBegin, tryEnd, pc, cp.getClass("java/lang/Throwable")));

                code_astore(localSlot0, out);
                
                //创建一个对象，并将其引用值压入栈顶
                out.writeByte(opc_new);
                out.writeShort(cp.getClass("java/lang/reflect/UndeclaredThrowableException"));

                //复制栈顶数值并将复制值压入栈顶
                out.writeByte(opc_dup);

                code_aload(localSlot0, out);
                
                //调用超类构造方法，实例初始化方法，私有方法
                out.writeByte(opc_invokespecial);
                out.writeShort(cp.getMethodRef("java/lang/reflect/UndeclaredThrowableException",
                    "<init>", "(Ljava/lang/Throwable;)V"));
                
                //将栈顶的异常抛出
                out.writeByte(opc_athrow);
            }

            if (minfo.code.size() > 65535) {
                throw new IllegalArgumentException("code size limit exceeded");
            }

            minfo.maxStack = 10;
            minfo.maxLocals = (short) (localSlot0 + 1);
            minfo.declaredExceptions = new short[exceptionTypes.length];
            for (int i = 0; i < exceptionTypes.length; i++) {
                minfo.declaredExceptions[i] = cp.getClass(dotToSlash(exceptionTypes[i].getName()));
            }

            return minfo;
        }

        /**
         * Generate code for wrapping an argument of the given type
         * whose value can be found at the specified local variable
         * index, in order for it to be passed (as an Object) to the
         * invocation handler's "invoke" method.  The code is written
         * to the supplied stream.
         */
        //生成用于包装给定类型参数的代码
        private void codeWrapArgument(Class type, int slot,
                                      DataOutputStream out)
            throws IOException
        {
        	//如果参数类型是原始类型
            if (type.isPrimitive()) {
                PrimitiveTypeInfo prim = PrimitiveTypeInfo.get(type);

                if (type == int.class ||
                    type == boolean.class ||
                    type == byte.class ||
                    type == char.class ||
                    type == short.class)
                {
                    code_iload(slot, out);   //加载int型变量到栈顶
                } else if (type == long.class) {
                    code_lload(slot, out);   //加载long型变量到栈顶
                } else if (type == float.class) {
                    code_fload(slot, out);   //加载fload型变量到栈顶
                } else if (type == double.class) {
                    code_dload(slot, out);   //加载double型变量到栈顶
                } else {
                    throw new AssertionError();
                }
                
                //调用静态方法, 并将返回值压入栈顶
                out.writeByte(opc_invokestatic);
                //调用对应包装类型的valueOf静态方法
                out.writeShort(cp.getMethodRef(prim.wrapperClassName, "valueOf", prim.wrapperValueOfDesc));

            } else {
            	//不是原始类型, 加载引用型变量到栈顶
                code_aload(slot, out);
            }
        }

        
        //将invoke方法中的返回值进行拆箱
        private void codeUnwrapReturnValue(Class type, DataOutputStream out)
            throws IOException
        {
            if (type.isPrimitive()) {
                PrimitiveTypeInfo prim = PrimitiveTypeInfo.get(type);
                //检验类型转换，检验未通过将抛出ClassCastException
                out.writeByte(opc_checkcast);
                out.writeShort(cp.getClass(prim.wrapperClassName));
                //调用实例方法
                out.writeByte(opc_invokevirtual);
                out.writeShort(cp.getMethodRef(
                    prim.wrapperClassName,
                    prim.unwrapMethodName, prim.unwrapMethodDesc));

                if (type == int.class ||
                    type == boolean.class ||
                    type == byte.class ||
                    type == char.class ||
                    type == short.class)
                {
                    out.writeByte(opc_ireturn);
                } else if (type == long.class) {
                    out.writeByte(opc_lreturn);
                } else if (type == float.class) {
                    out.writeByte(opc_freturn);
                } else if (type == double.class) {
                    out.writeByte(opc_dreturn);
                } else {
                    throw new AssertionError();
                }

            } else {
            	//检验类型转换，检验未通过将抛出ClassCastException
                out.writeByte(opc_checkcast);
                out.writeShort(cp.getClass(dotToSlash(type.getName())));
                //从当前方法返回对象引用
                out.writeByte(opc_areturn);
            }
        }

        
        //生成初始化代理类的静态字段的指令码
        private void codeFieldInitialization(DataOutputStream out)
            throws IOException
        {
        	//Class.forName(fromClass);
            codeClassForName(fromClass, out);
            
            //将方法名推送至栈顶
            code_ldc(cp.getString(methodName), out);
            
            //下面开始构建getMethod方法的参数了
            
            //将参数类型长度推送至栈顶
            code_ipush(parameterTypes.length, out);
            
            
            //创建一个Class型的数组，并将其引用值压入栈顶
            out.writeByte(opc_anewarray);
            out.writeShort(cp.getClass("java/lang/Class"));

            for (int i = 0; i < parameterTypes.length; i++) {
            	//复制栈顶数值并将复制值压入栈顶
                out.writeByte(opc_dup);
                //将i的值推送至栈顶
                code_ipush(i, out);

                if (parameterTypes[i].isPrimitive()) {
                    PrimitiveTypeInfo prim = PrimitiveTypeInfo.get(parameterTypes[i]);
                    //获取指定类的静态域，并将其值压入栈顶
                    //Class c = Integer.TYPE;
                    out.writeByte(opc_getstatic);
                    out.writeShort(cp.getFieldRef(prim.wrapperClassName, "TYPE", "Ljava/lang/Class;"));
                } else {
                	//Class c = parameterTypes[i];
                    codeClassForName(parameterTypes[i], out);
                }
                //将栈顶引用型数值存入指定数组的指定索引位置
                //cc[i] = c;
                out.writeByte(opc_aastore);
            }
            
            //调用实例方法, 将返回值压入栈顶
            //Method m = c.getMethod(name, parameterTypes);
            out.writeByte(opc_invokevirtual);
            out.writeShort(cp.getMethodRef(
                "java/lang/Class",
                "getMethod",
                "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;"));
            
            //为指定的类的静态域赋值
            //methodFieldName = m;
            out.writeByte(opc_putstatic);
            out.writeShort(cp.getFieldRef(dotToSlash(className), methodFieldName, "Ljava/lang/reflect/Method;"));
        }
    }

    //为代理类生成构造器方法
    private MethodInfo generateConstructor() throws IOException {
        MethodInfo minfo = new MethodInfo(
            "<init>", "(Ljava/lang/reflect/InvocationHandler;)V",
            ACC_PUBLIC);

        DataOutputStream out = new DataOutputStream(minfo.code);

        code_aload(0, out);

        code_aload(1, out);

        out.writeByte(opc_invokespecial);
        out.writeShort(cp.getMethodRef(
            superclassName,
            "<init>", "(Ljava/lang/reflect/InvocationHandler;)V"));

        out.writeByte(opc_return);

        minfo.maxStack = 10;
        minfo.maxLocals = 2;
        minfo.declaredExceptions = new short[0];

        return minfo;
    }

    //为代理类生成静态初始化方法
    private MethodInfo generateStaticInitializer() throws IOException {
        MethodInfo minfo = new MethodInfo("<clinit>", "()V", ACC_STATIC);

        int localSlot0 = 1;
        short pc, tryBegin = 0, tryEnd;

        DataOutputStream out = new DataOutputStream(minfo.code);

        for (List<ProxyMethod> sigmethods : proxyMethods.values()) {
            for (ProxyMethod pm : sigmethods) {
            	//为代理类的Method静态域生成初始化方法
                pm.codeFieldInitialization(out);
            }
        }

        out.writeByte(opc_return);

        tryEnd = pc = (short) minfo.code.size();

        minfo.exceptionTable.add(new ExceptionTableEntry(
            tryBegin, tryEnd, pc,
            cp.getClass("java/lang/NoSuchMethodException")));

        code_astore(localSlot0, out);

        out.writeByte(opc_new);
        out.writeShort(cp.getClass("java/lang/NoSuchMethodError"));

        out.writeByte(opc_dup);

        code_aload(localSlot0, out);

        out.writeByte(opc_invokevirtual);
        out.writeShort(cp.getMethodRef(
            "java/lang/Throwable", "getMessage", "()Ljava/lang/String;"));

        out.writeByte(opc_invokespecial);
        out.writeShort(cp.getMethodRef(
            "java/lang/NoSuchMethodError", "<init>", "(Ljava/lang/String;)V"));

        out.writeByte(opc_athrow);

        pc = (short) minfo.code.size();

        minfo.exceptionTable.add(new ExceptionTableEntry(
            tryBegin, tryEnd, pc,
            cp.getClass("java/lang/ClassNotFoundException")));

        code_astore(localSlot0, out);

        out.writeByte(opc_new);
        out.writeShort(cp.getClass("java/lang/NoClassDefFoundError"));

        out.writeByte(opc_dup);

        code_aload(localSlot0, out);

        out.writeByte(opc_invokevirtual);
        out.writeShort(cp.getMethodRef(
            "java/lang/Throwable", "getMessage", "()Ljava/lang/String;"));

        out.writeByte(opc_invokespecial);
        out.writeShort(cp.getMethodRef(
            "java/lang/NoClassDefFoundError",
            "<init>", "(Ljava/lang/String;)V"));

        out.writeByte(opc_athrow);

        if (minfo.code.size() > 65535) {
            throw new IllegalArgumentException("code size limit exceeded");
        }

        minfo.maxStack = 10;
        minfo.maxLocals = (short) (localSlot0 + 1);
        minfo.declaredExceptions = new short[0];

        return minfo;
    }


    /*
     * =============== Code Generation Utility Methods ===============
     */

    //下列方法为指定的本地变量生成由它们的名称表示的加载或存储操作的代码。代码被写入到提供的流中

    private void code_iload(int lvar, DataOutputStream out) throws IOException {
        codeLocalLoadStore(lvar, opc_iload, opc_iload_0, out);
    }

    private void code_lload(int lvar, DataOutputStream out) throws IOException {
        codeLocalLoadStore(lvar, opc_lload, opc_lload_0, out);
    }

    private void code_fload(int lvar, DataOutputStream out) throws IOException {
        codeLocalLoadStore(lvar, opc_fload, opc_fload_0, out);
    }

    private void code_dload(int lvar, DataOutputStream out) throws IOException {
        codeLocalLoadStore(lvar, opc_dload, opc_dload_0, out);
    }

    private void code_aload(int lvar, DataOutputStream out) throws IOException {
        codeLocalLoadStore(lvar, opc_aload, opc_aload_0, out);
    }

//  private void code_istore(int lvar, DataOutputStream out)throws IOException{
//      codeLocalLoadStore(lvar, opc_istore, opc_istore_0, out);
//  }

//  private void code_lstore(int lvar, DataOutputStream out) throws IOException {
//      codeLocalLoadStore(lvar, opc_lstore, opc_lstore_0, out);
//  }

//  private void code_fstore(int lvar, DataOutputStream out) throws IOException {
//      codeLocalLoadStore(lvar, opc_fstore, opc_fstore_0, out);
//  }

//  private void code_dstore(int lvar, DataOutputStream out) throws IOException {
//      codeLocalLoadStore(lvar, opc_dstore, opc_dstore_0, out);
//  }

    private void code_astore(int lvar, DataOutputStream out) throws IOException{
        codeLocalLoadStore(lvar, opc_astore, opc_astore_0, out);
    }

    /**
     * Generate code for a load or store instruction for the given local
     * variable.  The code is written to the supplied stream.
     *
     * "opcode" indicates the opcode form of the desired load or store
     * instruction that takes an explicit local variable index, and
     * "opcode_0" indicates the corresponding form of the instruction
     * with the implicit index 0.
     */
    //为给定的本地变量生成存储或加载指令代码，这个代码会写入到给定的流中
    private void codeLocalLoadStore(int lvar, int opcode, int opcode_0, DataOutputStream out) throws IOException {
    	//lvar为0
    	//opcode
    	//1.将指定的引用类型本地变量推送至栈顶  opc_aload
    	//2.将栈顶引用型数值存入指定本地变量   opc_astore
    	//opcode_0
    	//1.将第一个引用类型本地变量推送至栈顶  opc_aload_0
    	//2.将栈顶引用型数值存入第一个本地变量  opc_astore_0
        assert lvar >= 0 && lvar <= 0xFFFF;
        if (lvar <= 3) {
            out.writeByte(opcode_0 + lvar);
        } else if (lvar <= 0xFF) {
            out.writeByte(opcode);
            out.writeByte(lvar & 0xFF);
        } else {
            /*
             * Use the "wide" instruction modifier for local variable
             * indexes that do not fit into an unsigned byte.
             */
        	//用"wide"指令修改不适合无符号字节的本地变量索引
            out.writeByte(opc_wide);
            //将指定的引用类型本地变量推送至栈顶
            out.writeByte(opcode);
            out.writeShort(lvar & 0xFFFF);
        }
    }

    /**
     * Generate code for an "ldc" instruction for the given constant pool
     * index (the "ldc_w" instruction is used if the index does not fit
     * into an unsigned byte).  The code is written to the supplied stream.
     */
    //给定常量池的索引, 生成ldc指令(将给定的常量值推送至栈顶)
    private void code_ldc(int index, DataOutputStream out) throws IOException {
        assert index >= 0 && index <= 0xFFFF;
        if (index <= 0xFF) {
        	//将int,float或String型常量值从常量池中推送至栈顶
            out.writeByte(opc_ldc);
            out.writeByte(index & 0xFF);
        } else {
        	//将int,float或String型常量值从常量池中推送至栈顶(宽索引)
            out.writeByte(opc_ldc_w);
            out.writeShort(index & 0xFFFF);
        }
    }

    //生成推送整形常量到栈顶的方法, 使用"iconst_<i>","bipush"或"sipush"指令依赖于值的大小
    private void code_ipush(int value, DataOutputStream out) throws IOException {
        if (value >= -1 && value <= 5) {
        	//将int型0推送至栈顶
            out.writeByte(opc_iconst_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
        	//将单字节的常量值(-128~127)推送至栈顶
            out.writeByte(opc_bipush);
            out.writeByte(value & 0xFF);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
        	//将一个短整型常量值(-32768~32767)推送至栈顶
            out.writeByte(opc_sipush);
            out.writeShort(value & 0xFFFF);
        } else {
            throw new AssertionError();
        }
    }

    //生成调用Class.forName的方法代码
    private void codeClassForName(Class cl, DataOutputStream out) throws IOException {
        //将类名的在常量池中索引推送至栈顶
    	code_ldc(cp.getString(cl.getName()), out);
        //调用静态方法
        out.writeByte(opc_invokestatic);
        out.writeShort(cp.getMethodRef("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;"));
    }


    /*
     * ==================== General Utility Methods ====================
     */

    //将点转换成斜线
    private static String dotToSlash(String name) {
        return name.replace('.', '/');
    }

    //返回方法描述字符串, 通过参数类型列表和返回类型
    private static String getMethodDescriptor(Class[] parameterTypes, Class returnType) {
        return getParameterDescriptors(parameterTypes) +
            ((returnType == void.class) ? "V" : getFieldType(returnType));
    }

    //获取方法参数描述字符串
    private static String getParameterDescriptors(Class[] parameterTypes) {
        StringBuilder desc = new StringBuilder("(");
        for (int i = 0; i < parameterTypes.length; i++) {
            desc.append(getFieldType(parameterTypes[i]));
        }
        desc.append(')');
        return desc.toString();
    }

    //返回字段类型的描述字符串
    private static String getFieldType(Class type) {
        if (type.isPrimitive()) {
        	//type是基本类型
            return PrimitiveTypeInfo.get(type).baseTypeString;
        } else if (type.isArray()) {
        	//type是数组类型
            return type.getName().replace('.', '/');
        } else {
        	//type是对象类型
            return "L" + dotToSlash(type.getName()) + ";";
        }
    }

    //返回友好的方法签名, 通过方法名称和参数类型列表
    private static String getFriendlyMethodSignature(String name, Class[] parameterTypes) {
        StringBuilder sig = new StringBuilder(name);
        sig.append('(');
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                sig.append(',');
            }
            Class parameterType = parameterTypes[i];
            int dimensions = 0;
            while (parameterType.isArray()) {
                parameterType = parameterType.getComponentType();
                dimensions++;
            }
            sig.append(parameterType.getName());
            while (dimensions-- > 0) {
                sig.append("[]");
            }
        }
        sig.append(')');
        return sig.toString();
    }

    /**
     * Return the number of abstract "words", or consecutive local variable
     * indexes, required to contain a value of the given type.  See JVMS
     * section 3.6.1.
     *
     * Note that the original version of the JVMS contained a definition of
     * this abstract notion of a "word" in section 3.4, but that definition
     * was removed for the second edition.
     */
    private static int getWordsPerType(Class type) {
    	//如果是long或double类型则返回2, 其他返回1
        if (type == long.class || type == double.class) {
            return 2;
        } else {
            return 1;
        }
    }

    //收集兼容类型的方法
    private static void collectCompatibleTypes(Class<?>[] from, Class<?>[] with, List<Class<?>> list) {
        for (int i = 0; i < from.length; i++) {
        	//首先判断列表是否包含from
            if (!list.contains(from[i])) {
                for (int j = 0; j < with.length; j++) {
                	//from和with是否相同或者是with的子类型
                    if (with[j].isAssignableFrom(from[i])) {
                    	//是的话就将from添加到列表
                        list.add(from[i]);
                        break;
                    }
                }
            }
        }
    }

    
    //计算唯一的异常捕获列表
    private static List<Class<?>> computeUniqueCatchList(Class<?>[] exceptions) {
    	List<Class<?>> uniqueList = new ArrayList<Class<?>>();
    	//总是捕获Error和RuntimeException异常
        uniqueList.add(Error.class);
        uniqueList.add(RuntimeException.class);
    nextException:
        for (int i = 0; i < exceptions.length; i++) {
            Class<?> ex = exceptions[i];
            //如果异常是Throwable派生而来的
            if (ex.isAssignableFrom(Throwable.class)) {
            	//如果代理方法声明了抛出Throwable, 那么就没有必须捕获的代码块
                uniqueList.clear();
                break;
            } else if (!Throwable.class.isAssignableFrom(ex)) {
                //忽略调用方法不能抛出的类型
                continue;
            }
            //再次将异常和当前的异常列表里需要被捕获的异常进行比较
            for (int j = 0; j < uniqueList.size();) {
                Class<?> ex2 = uniqueList.get(j);
                //如果当前异常的父类异常已经存在在列表中, 就忽略该异常, 并继续到nextException处调用
                if (ex2.isAssignableFrom(ex)) {
                    continue nextException;
                } else if (ex.isAssignableFrom(ex2)) {
                	//如果当前异常的子类在列表中, 那么就移除它
                    uniqueList.remove(j);
                } else {
                	//否则继续比较
                    j++;
                }
            }
            //到了这里代表该异常是唯一需要不活的, 那么就把它添加到列表中
            uniqueList.add(ex);
        }
        return uniqueList;
    }

    
    //基本类型信息
    private static class PrimitiveTypeInfo {

    	//基本类型字符，用于各种描述符
        public String baseTypeString;
        //对应包装器类的名称
        public String wrapperClassName;
        //包装类valueOf工厂方法的描述符
        public String wrapperValueOfDesc;
        //用于检索原始值的包装器类方法的名称
        public String unwrapMethodName;
        //同一方法的描述符
        public String unwrapMethodDesc;

        private static Map<Class,PrimitiveTypeInfo> table = new HashMap<Class,PrimitiveTypeInfo>();
        
        static {
            add(byte.class, Byte.class);
            add(char.class, Character.class);
            add(double.class, Double.class);
            add(float.class, Float.class);
            add(int.class, Integer.class);
            add(long.class, Long.class);
            add(short.class, Short.class);
            add(boolean.class, Boolean.class);
        }

        //添加原始类型到包装类型的映射
        private static void add(Class primitiveClass, Class wrapperClass) {
            table.put(primitiveClass, new PrimitiveTypeInfo(primitiveClass, wrapperClass));
        }

        private PrimitiveTypeInfo(Class primitiveClass, Class wrapperClass) {
            //判断传入的是基本类型
        	assert primitiveClass.isPrimitive();
            baseTypeString = Array.newInstance(primitiveClass, 0).getClass().getName().substring(1);
            wrapperClassName = dotToSlash(wrapperClass.getName());
            wrapperValueOfDesc = "(" + baseTypeString + ")L" + wrapperClassName + ";";
            unwrapMethodName = primitiveClass.getName() + "Value";
            unwrapMethodDesc = "()" + baseTypeString;
        }

        //根据包装类型获取原始类型
        public static PrimitiveTypeInfo get(Class cl) {
            return table.get(cl);
        }
    }


    private static class ConstantPool {

    	//用于存储常量项的集合
        private List<Entry> pool = new ArrayList<Entry>(32);

        //常量池数据和对应索引的映射表
        private Map<Object,Short> map = new HashMap<Object,Short>(16);

        //如果不添加新的常数池条目则为true
        private boolean readOnly = false;

        //获取或分配CONSTANT_Utf8常量项在常量池中的索引
        public short getUtf8(String s) {
            if (s == null) {
                throw new NullPointerException();
            }
            return getValue(s);
        }

        //获取或分配CONSTANT_Integer常量项在常量池中的索引
        public short getInteger(int i) {
            return getValue(new Integer(i));
        }

        //获取或分配CONSTANT_Float常量项在常量池中的索引
        public short getFloat(float f) {
            return getValue(new Float(f));
        }

        //获取或分配CONSTANT_Class常量项在常量池中的索引
        public short getClass(String name) {
        	//获取全限定名常量项的索引
            short utf8Index = getUtf8(name);
            //获取CONSTANT_Class_info的索引
            return getIndirect(new IndirectEntry(CONSTANT_CLASS, utf8Index));
        }

        //获取或分配CONSTANT_String常量项在常量池中的索引
        public short getString(String s) {
        	//获取字符串字面量的索引
            short utf8Index = getUtf8(s);
            //CONSTANT_STRING的索引
            return getIndirect(new IndirectEntry(CONSTANT_STRING, utf8Index));
        }

        //获取或分配CONSTAT_FieldRef常量项在常量池中的索引
        public short getFieldRef(String className,String name, String descriptor){
        	//根据类名获取常量池中的索引
            short classIndex = getClass(className);
            short nameAndTypeIndex = getNameAndType(name, descriptor);
            return getIndirect(new IndirectEntry(CONSTANT_FIELD, classIndex, nameAndTypeIndex));
        }

        //获取或分配CONSTANT_MethodRef常量项在常量池中的索引
        public short getMethodRef(String className, String name, String descriptor) {
            //获取类名的索引
        	short classIndex = getClass(className);
        	//获取CONSTANT_NameAndType的索引
            short nameAndTypeIndex = getNameAndType(name, descriptor);
            //方法引用类型还包含了方法所属类名索引, 方法名称和类型索引
            return getIndirect(new IndirectEntry(CONSTANT_METHOD, classIndex, nameAndTypeIndex));
        }

        //获取或分配CONSTANT_InterfaceMethodRef常量项在常量池中的索引
        public short getInterfaceMethodRef(String className, String name, String descriptor) {
            short classIndex = getClass(className);
            short nameAndTypeIndex = getNameAndType(name, descriptor);
            return getIndirect(new IndirectEntry(CONSTANT_INTERFACEMETHOD, classIndex, nameAndTypeIndex));
        }

        //获取或分配CONSTANT_NameAndType常量项在常量池中的索引
        public short getNameAndType(String name, String descriptor) {
            //获取方法名字面量在常量池中的索引
        	short nameIndex = getUtf8(name);
        	//获取方法描述符字面量在常量池中的索引
            short descriptorIndex = getUtf8(descriptor);
            //CONSTANT_NameAndType类型还包含其他引用类型
            return getIndirect(new IndirectEntry(CONSTANT_NAMEANDTYPE, nameIndex, descriptorIndex));
        }

        //设置常量池只读
        public void setReadOnly() {
            readOnly = true;
        }

        //将常量池写入流中,作为生成的class文件的一部分
        public void write(OutputStream out) throws IOException {
            DataOutputStream dataOut = new DataOutputStream(out);
            //先写入常量池计数器, 它的值为常量池大小再加上1
            dataOut.writeShort(pool.size() + 1);
            for (Entry e : pool) {
            	//再循环写入常量池的每一项
                e.write(dataOut);
            }
        }

        //添加一个新的常量项并返回常量池大小
        private short addEntry(Entry entry) {
            pool.add(entry);
            if (pool.size() >= 65535) {
                throw new IllegalArgumentException("constant pool size limit exceeded");
            }
            return (short) pool.size();
        }

        
        //为传入的字面量值获取或分配索引
        private short getValue(Object key) {
        	//到map中获取对应的索引
            Short index = map.get(key);
            if (index != null) {
            	//如果常量池有对应的值则返回索引
                return index.shortValue();
            } else {
                if (readOnly) {
                    throw new InternalError("late constant pool addition: " + key);
                }
                //否则添加一个新的常量项并返回索引
                short i = addEntry(new ValueEntry(key));
                map.put(key, new Short(i));
                return i;
            }
        }

        //为一个包含其他常量项的类型实体获取或分配索引
        private short getIndirect(IndirectEntry e) {
        	//在映射表中获取该索引
            Short index = map.get(e);
            if (index != null) {
            	//如果有值就直接返回
                return index.shortValue();
            } else {
                if (readOnly) {
                    throw new InternalError("late constant pool addition");
                }
                //否则添加一个值到常量池中
                short i = addEntry(e);
                //在映射表中建立关联关系
                map.put(e, new Short(i));
                //返回索引
                return i;
            }
        }

        //Entry是所有常量池类型的抽象父类, 它定义了向class文件写入常量池实体的抽象方法
        private static abstract class Entry {
            public abstract void write(DataOutputStream out) throws IOException;
        }

        //ValueEntry代表常量池中的仅包含直接值的常量项
        //例如：CONSTANT_INTEGER, CONSTANT_FLOAT, CONSTANT_LONG, CONSTANT_DOUBLE
        private static class ValueEntry extends Entry {
            private Object value;

            public ValueEntry(Object value) {
                this.value = value;
            }

            public void write(DataOutputStream out) throws IOException {
                if (value instanceof String) {
                    out.writeByte(CONSTANT_UTF8);
                    out.writeUTF((String) value);
                } else if (value instanceof Integer) {
                    out.writeByte(CONSTANT_INTEGER);
                    out.writeInt(((Integer) value).intValue());
                } else if (value instanceof Float) {
                    out.writeByte(CONSTANT_FLOAT);
                    out.writeFloat(((Float) value).floatValue());
                } else if (value instanceof Long) {
                    out.writeByte(CONSTANT_LONG);
                    out.writeLong(((Long) value).longValue());
                } else if (value instanceof Double) {
                    out.writeDouble(CONSTANT_DOUBLE);
                    out.writeDouble(((Double) value).doubleValue());
                } else {
                    throw new InternalError("bogus value entry: " + value);
                }
            }
        }

        
        //IndirectEntry代表常量池中包含其他引用的常量项
        //例如：CONSTANT_Class, CONSTANT_String, CONSTANT_Fieldref,
        //CONSTANT_Methodref, CONSTANT_InterfaceMethodref, CONSTANT_NameAndType
        private static class IndirectEntry extends Entry {
            private int tag;       //常量项类型标记
            private short index0;  //第一个索引
            private short index1;  //第二个索引

            //包含一个引用的常量项的构造器
            public IndirectEntry(int tag, short index) {
                this.tag = tag;
                this.index0 = index;
                this.index1 = 0;
            }

            //包含两个引用的常量项的构造器
            public IndirectEntry(int tag, short index0, short index1) {
                this.tag = tag;
                this.index0 = index0;
                this.index1 = index1;
            }

            //向流中写入实体
            public void write(DataOutputStream out) throws IOException {
                //写入标志号
            	out.writeByte(tag);
            	//写入第一个索引
                out.writeShort(index0);
                //如果包含第二个索引那么就写入第二个索引
                if (tag == CONSTANT_FIELD || tag == CONSTANT_METHOD || 
                	tag == CONSTANT_INTERFACEMETHOD || tag == CONSTANT_NAMEANDTYPE) {
                    out.writeShort(index1);
                }
            }

            public int hashCode() {
                return tag + index0 + index1;
            }

            public boolean equals(Object obj) {
                if (obj instanceof IndirectEntry) {
                    IndirectEntry other = (IndirectEntry) obj;
                    if (tag == other.tag && index0 == other.index0 && index1 == other.index1) {
                        return true;
                    }
                }
                return false;
            }
        }
    }
}
