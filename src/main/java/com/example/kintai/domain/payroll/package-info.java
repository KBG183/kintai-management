/**
 * 給与計算・精算ドメイン (Payroll Domain)
 * - 日本労働基準法第37条に基づく割増賃金（深夜25%、時間外25%、重複50%）自動計算エンジン
 * - 分（Minute）単位の整数計算および BigDecimal による端数処理（50銭ルール）
 * - Apache POI を利用した月次給与台帳の Excel 一括エクスポート
 */
package com.example.kintai.domain.payroll;
